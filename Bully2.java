//package bully2;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bully leader election algorithm.
 *
 * @author Aditya Advani
 */
public class Bully2 extends UnicastRemoteObject implements BIF, Serializable {

    //information about current node
    static String NodeName = "";
    static String NodeIP = "";
    static int NodeID = 0;
    static String Leader = "NewLeader";
    static int LeaderID = 0;

    //process Maps
    static HashMap<Integer, String> process = new HashMap<>();
    static HashMap<String, String> processName = new HashMap<>();

    //network varibles
    static final int port = 7394;
    static Registry r;

    //class static object
    static Bully2 B;

    //election variables
    static boolean gotReplyMessage = false;
    static boolean gotCoordMessage = false;
    static boolean LeaderIsAlive = false;
    static boolean ElectionInProgress = false;
    static boolean ReElectionInProgress = false;
    static final int CTimeoutLimit = 5000;
    static final int RTimeoutLimit = 2000;
    static final int BeatTimer = 5000;
    static Thread ReplyTimeoutTracker;
    static Thread CoordinatorTimeoutTracker;
    static int ElectionIdentifier = 0;

    public Bully2() throws RemoteException {
        super();
    }

    /**
     * New process sends an add request to any(If only to leader, uncomment the
     * commented code) node. this node then finds a new id for the new node. it
     * adds the node's details to its maps and sends out an updated map to all
     * nodes, including the new node. It then sends the new node the leader
     * details and its new id.
     *
     * @param name
     * @param ip
     * @return
     * @throws java.rmi.RemoteException
     */
    @Override
    public boolean addNewProcess(String name, String ip) throws RemoteException {
        System.out.println("\nNew node add request");
//        if (NodeID != LeaderID) {
//            return false;
//        } else {
        int id = 1;
        //get the lowest available id
        for (int i : process.keySet()) {
            for (int j : process.keySet()) {
                if (id == j) {
                    id++;
                } else {
                    break;
                }
            }
        }

        //update current node's maps
        String node;
        process.put(id, ip);
        processName.put(ip, name);

        //update maps of every node in the network (including new node)
        for (int i
                : process.keySet()) {
            if (NodeID == id) {
                continue;
            }
            node = process.get(i);
            try {
                r = LocateRegistry.getRegistry(node, port);
                BIF pro = (BIF) r.lookup("process");
                pro.updateProcessMaps(process, processName);
            } catch (NotBoundException | AccessException ex) {
            } catch (RemoteException ex) {
            }
        }

        //send other required info to new node
        try {
            r = LocateRegistry.getRegistry(ip, port);
            BIF pro = (BIF) r.lookup("process");
            pro.updateNewNode(id, Leader, LeaderID);
        } catch (NotBoundException | AccessException ex) {
        } catch (RemoteException ex) {
        }

        return true;
//        }
    }

    /**
     * Method updates the maps of the process where the method is called on.
     *
     * @param map
     * @param mapName
     * @throws RemoteException
     */
    @Override
    public void updateProcessMaps(HashMap<Integer, String> map, HashMap<String, String> mapName) throws RemoteException {
        process = map;
        processName = mapName;

        System.out.println("ProcessName: " + processName);
    }

    /**
     * Method updates the fields of the New Process.
     *
     * @param Id ID of the New Process
     * @param LIp IPAddress of the current Leader Process
     * @param LId ID of the current Leader Process
     * @throws RemoteException
     */
    @Override
    public void updateNewNode(int Id, String LIp, int LId) throws RemoteException {
        NodeID = Id;
        Leader = LIp;
        LeaderID = LId;
    }

    /**
     * Method check if the node called on is alive.
     *
     * @return
     * @throws RemoteException
     */
    @Override
    public boolean IsLeaderAlive() throws RemoteException {
        return true;
    }

    /**
     * Method handles the beginning of a new Election/ReElection.
     */
    public void startElection() {
        //if election is not currently running
        if (!ElectionInProgress) {
            if (ReElectionInProgress) {
                System.out.println("\nStarting ReElection");
            } else {
                System.out.println("\nStarting Election");
            }
            ElectionInProgress = true;
            gotReplyMessage = false;
            gotCoordMessage = false;

            ArrayList<String> sent = new ArrayList<>();
            String next;

            //start a timeout thread for Reply message and a timeout thread for Coordinator message
            CoordinatorTimeoutTracker = new Thread(new ReElectionTimer());
            CoordinatorTimeoutTracker.start();
            ReplyTimeoutTracker = new Thread(new ReplyTimer());
            ReplyTimeoutTracker.start();

            //send election message to every process with ID higher than current process's ID
            for (Integer i : process.keySet()) {
                if (NodeID == i) {
                    continue;
                }
                if (NodeID < i) {
                    try {
                        next = process.get(i);
                        if (sent.contains(next)) {
                            continue;
                        }
                        sent.add(next);
                        r = LocateRegistry.getRegistry(next, port);
                        BIF node = (BIF) r.lookup("process");
                        node.electionMessage(NodeIP, NodeName);
                        System.out.println("sent election message to " + processName.get(process.get(i)));
                    } catch (NotBoundException | AccessException ex) {
                    } catch (RemoteException ex) {
                    }
                }
            }
        }
    }

    /**
     * Method handles sending of election message.
     *
     * @param IP
     * @param name
     * @throws RemoteException
     */
    @Override
    public void electionMessage(String IP, String name) throws RemoteException {
        System.out.println("received election message from " + name);
        //if election message is the first one for this round of election
        if (!ElectionInProgress) {
            ElectionInProgress = true;
            gotReplyMessage = false;
            gotCoordMessage = false;
            System.out.println("entering new election round");

            //start the two timeout threads
            CoordinatorTimeoutTracker = new Thread(new ReElectionTimer());
            CoordinatorTimeoutTracker.start();
            ReplyTimeoutTracker = new Thread(new ReplyTimer());
            ReplyTimeoutTracker.start();

            ArrayList<String> sent = new ArrayList<>();
            String next;
            boolean replied = false;

            for (int i : process.keySet()) {
                //if node(i) is current node
                if (i == NodeID) {
                    continue;
                    //send a reply to the process that sent the election message
                } else if (process.get(i).equals(IP)) {
                    try {
                        if (replied) {
                            continue;
                        }
                        replied = true;
                        r = LocateRegistry.getRegistry(IP, port);
                        BIF node = (BIF) r.lookup("process");
                        node.responseMessage(NodeName);
                    } catch (NotBoundException | AccessException ex) {
                    } catch (RemoteException ex) {
                    }
                    //if node(i) has larger id than current node, send election message
                } else if (i > NodeID) {
                    try {
                        next = process.get(i);
                        if (sent.contains(next)) {
                            continue;
                        }
                        sent.add(next);
                        r = LocateRegistry.getRegistry(next, port);
                        BIF node = (BIF) r.lookup("process");
                        node.electionMessage(NodeIP, NodeName);
                        System.out.println("sent election message to " + processName.get(next));
                    } catch (NotBoundException | AccessException ex) {
                    } catch (RemoteException ex) {
                    }
                }
            }
            //if the election message is not the first for this round of elections
        } else {
            //send a reply to the process that sent the election message
            try {
                r = LocateRegistry.getRegistry(IP, port);
                BIF node = (BIF) r.lookup("process");
                node.responseMessage(NodeName);
            } catch (NotBoundException | AccessException ex) {
            } catch (RemoteException ex) {
            }
        }
    }

    /**
     * Method handles events on receiving a reply.
     *
     * @param name
     * @throws RemoteException
     */
    @Override
    public void responseMessage(String name) throws RemoteException {
        System.out.println("got response from " + name);
        //record receiving a reply
        gotReplyMessage = true;
        //stop the reply timeout thread
        if (ReplyTimeoutTracker.isAlive() && !ReplyTimeoutTracker.isInterrupted()) {
            System.out.println("reply received, interrupting reply timeout tracker thread");
            ReplyTimeoutTracker.interrupt();
        }
    }

    /**
     * Method handles proclaiming self as the new leader and sending out
     * coordinator messages to every other process.
     */
    public void coordinator() {
        System.out.println("Node is Leader");
        Leader = NodeIP;
        LeaderID = NodeID;
        System.out.println("Sending coordinator message");
        ElectionInProgress = false;
        ReElectionInProgress = false;
        //record sending coordinator message
        gotCoordMessage = true;
        //stop the Coordinator message timer thread
        if (CoordinatorTimeoutTracker.isAlive() && !CoordinatorTimeoutTracker.isInterrupted()) {
            CoordinatorTimeoutTracker.interrupt();
        }

        //send coordinator message to every node on the network
        for (int i : process.keySet()) {
            if (!process.get(i).equals(NodeIP)) {
                try {
                    r = LocateRegistry.getRegistry(process.get(i), port);
                    BIF node = (BIF) r.lookup("process");
                    node.CoordinatorMessage(NodeIP, NodeID);
                } catch (NotBoundException | AccessException ex) {
                } catch (RemoteException ex) {
                }
            }
        }
        System.out.println("Leader IP Address: " + NodeIP);
    }

    /**
     * Method handles sending coordinator message and causal events at receiving
     * process.
     *
     * @param NewLeaderIP
     * @param NewLeaderID
     * @throws RemoteException
     */
    @Override
    public void CoordinatorMessage(String NewLeaderIP, int NewLeaderID) throws RemoteException {
        if (!NewLeaderIP.equals(NodeIP)) {
            System.out.println("received coordinator message from " + processName.get(NewLeaderIP));
        }
        ElectionInProgress = false;
        ReElectionInProgress = false;
        gotCoordMessage = true;
        gotReplyMessage = true;
        //assign new leader
        Leader = NewLeaderIP;
        LeaderID = NewLeaderID;
        if (!NewLeaderIP.equals(NodeIP)) {
            System.out.println("Leader: " + Leader + " LeaderID : " + LeaderID);
        }
    }

    /**
     * Method handles the event where a ReElection needs to be started.
     */
    public void reStartElection() {
        ReElectionInProgress = true;
        ElectionInProgress = false;
        //reset stats of every process currently alive
        for (int i : process.keySet()) {
            if (i != NodeID) {
                try {
                    r = LocateRegistry.getRegistry(process.get(i), port);
                    BIF node = (BIF) r.lookup("process");
                    node.endElection();
                } catch (NotBoundException | AccessException ex) {
                } catch (RemoteException ex) {
                }
            }
        }
        //remove reference from previous object to garbage collect it
        try {
            B = new Bully2();
        } catch (RemoteException ex) {
        }
        //start a new election
        B.startElection();
    }

    /**
     * Method resets the election stats of the process the method is called on.
     *
     * @throws RemoteException
     */
    @Override
    public void endElection() throws RemoteException {
        ReElectionInProgress = true;
        ElectionInProgress = false;
        gotCoordMessage = true;
        gotReplyMessage = true;
        try {
            B = new Bully2();
        } catch (RemoteException ex) {
        }
    }

    /**
     * Main method handles new process booting up and starting the heartbeat
     * checker thread.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        //Instantiating the class object
        try {
            B = new Bully2();
        } catch (RemoteException ex) {
        }

        //ID the process
        try {
            InetAddress IP = InetAddress.getLocalHost();
            NodeName = IP.getHostName();
            NodeIP = IP.getHostAddress();
            System.out.println("*** Process Identification Completed ***");
            System.out.println("Process Name: " + NodeName + "\nProcess IP: " + NodeIP);
        } catch (UnknownHostException ex) {
        }

        // Start registry
        try {
            r = LocateRegistry.createRegistry(port);
            r.rebind("process", new Bully2());

        } catch (RemoteException ex) {
        }

        if (args[0].equals("-f")) {
            //first process in the network
            NodeID = 1;
            process.put(NodeID, NodeIP);
            processName.put(NodeIP, NodeName);
            Leader = NodeIP;
            LeaderID = NodeID;
        } else {
            //args[0] is the IPAddress of the node to send the add request to.
            //not first process in the network

            String IP = args[0];
            boolean SuccessfulAdd = false;
            do {
                try {
                    r = LocateRegistry.getRegistry(IP, port);
                    BIF pro = (BIF) r.lookup("process");
                    SuccessfulAdd = pro.addNewProcess(NodeName, NodeIP);
                    System.out.println("Successfully added to the network.");
                } catch (NotBoundException | AccessException ex) {
                    System.out.println("\nCould not add to the network.");
                    System.out.print("Enter valid IP: ");
                } catch (RemoteException ex) {
                    System.out.println("\nCould not add to the network.");
                    System.out.print("Enter valid IP: ");
                }
            } while (!SuccessfulAdd);
        }

        System.out.println("LeaderIP: " + Leader + " LeaderID: " + LeaderID + " SelfID: " + NodeID);

        Thread LeaderCheck = new Thread(new HeartBeat());
        LeaderCheck.start();

    }

    /**
     * Class HeartBeat implements a runnable thread that checks if the leader is
     * up and running, if not, it sends out a request to start a new election.
     */
    public static class HeartBeat implements Runnable {

        @Override
        public void run() {
            while (true) {
                System.out.print("");
                //wait if election is in progress
                LeaderIsAlive = true;
                if (!NodeIP.equals(Leader) && !ElectionInProgress && !ReElectionInProgress) {
                    //check if leader is alive
                    try {
                        System.out.println("\nchecking if Leader " + processName.get(Leader) + " is alive");
                        r = LocateRegistry.getRegistry(Leader, port);
                        BIF node = (BIF) r.lookup("process");
                        LeaderIsAlive = node.IsLeaderAlive();
                    } catch (NotBoundException | AccessException ex) {
                        LeaderIsAlive = false;
                    } catch (RemoteException ex) {
                        LeaderIsAlive = false;
                    }

                    if (LeaderIsAlive && !Leader.equals("")) {
                        System.out.println("Leader " + processName.get(Leader) + " is alive");
                    } else {
                        System.out.println("Leader is down");
                        LeaderIsAlive = false;
                    }
                }
                if ((NodeID > LeaderID) || (!LeaderIsAlive && !ElectionInProgress && !ReElectionInProgress)) {
                    //start election
                    if ((NodeID > LeaderID) && !ElectionInProgress && !ReElectionInProgress) {
                        System.out.println("ID greater than Leader's ID");
                    }
                    B.startElection();
                } else {
                    //wait till next check cycle
                    try {
                        int randomWaitTime = 1000 + (int) (Math.random() * BeatTimer);
                        Thread.sleep(randomWaitTime);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Bully2.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

        }
    }

    /**
     * Class ReElectionTimer implements runnable thread to handle the timeout
     * event if a coordinator is not received after a certain time of starting a
     * new election.
     */
    public static class ReElectionTimer implements Runnable {

        @Override
        public void run() {
            try {
                int randomWaitTime = CTimeoutLimit + (int) (Math.random() * CTimeoutLimit);
                Thread.sleep(randomWaitTime);
                //if a coordinator message is received, the catch method detects the interrupt message sent and ends the thread.
            } catch (InterruptedException ex) {
                System.out.println("interrupt received, stopping ReElection timeout tracker thread.");
                gotCoordMessage = true;
            }
            //if no coordinator message is received till timeout, a ReElection starts.
            if (!gotCoordMessage) {
                System.out.println("restarting election");
                B.reStartElection();
            }
        }
    }

    /**
     * Class ReplyTimer implements a runnable thread to handle the timeout event
     * if a reply is not received after a certain time of starting a new
     * election.
     */
    public static class ReplyTimer implements Runnable {

        @Override
        public void run() {
            gotReplyMessage = false;
            try {
                int randomWaitTime = RTimeoutLimit + (int) (Math.random() * RTimeoutLimit);
                Thread.sleep(randomWaitTime);
                //if a reply message is received, the catch method detects the interrupt message sent and ends the thread.
            } catch (InterruptedException ex) {
                System.out.println("interrupt received, stopping Reply timeout tracker thread.");
                gotReplyMessage = true;
            }
            //if no reply message is received till timeout, current process becomes new leader.
            if (!gotReplyMessage) {
                System.out.println("sending coordinator message");
                B.coordinator();
            }
        }
    }

}
