//package bully2;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

/**
 * RMI interface for Bully leader election algorithm.
 *
 * @author Aditya Advani
 */
public interface BIF extends Remote {

    public void updateProcessMaps(HashMap<Integer, String> map, HashMap<String, String> mapName) throws RemoteException;

    public void updateNewNode(int Id, String LIp, int LId) throws RemoteException;

    public boolean addNewProcess(String name, String ip) throws RemoteException;

    public boolean IsLeaderAlive() throws RemoteException;

    public void electionMessage(String IP, String name) throws RemoteException;

    public void responseMessage(String name) throws RemoteException;

    public void CoordinatorMessage(String NewLeaderIP, int NewLeaderID) throws RemoteException;

    public void endElection() throws RemoteException;
}
