# Bully Leader Election

## Compiling the code:
Enter the directory where the .java files are stored, open a terminal at that location and enter the following command-
'javac *.java'

## Running the code:
The code takes 2 types of commands for starting-
1. 'java Bully2 -f'
  * used when a new network has to be started (first node)
2. 'java Bully2 [IP of any currently active node]'
  * used when a new process is to be added to an existing network (not first node)

## Basic working:
The process with currently the highest ID starts an election and becomes a leader. When this process goes down, the process with the next highest ID becomes the new leader or if another process starts with an ID higher than the current Leader's ID, it becomes the new leader. To check if the leader is up and running, all the other processes send a heartbeat to the leader node. If leader node fails to reply, the first node to detect this initialtes an election. If there are multiple failures during the election process causing a deadblock kind of a situation, after a timeout, the first process to timeout restarts the process with a new election. While the election goes on, if a process does not receive any replies within a specific time period, it procaims itself to be the new leader and sends out a coordinator message to every other node in the network.

## Explaining the screenshots (running examples):

### 'Join and Failure causing Election.png'

![See Image](https://github.com/adityaadvani/Bully-leader-election/blob/master/join%20and%20Failure%20causing%20Election.png "Join and Failure causing Election")

ID value - process name mapping-
1. glados
2. california
3. rhea
4. maine
5. newyork

The current leader is maine(4) initially, a new process newyork(5) joins the network. newyork, realizes its ID is higher than that of the current leader. newyork starts an election and becomes the new leader. while everyone is sending a heartbeat to the new leader, rhea(2) realizes that the leader has failed and starts a new election to appoint a new leader.

### 'Failure causing ReElection.png'
![See Image](https://github.com/adityaadvani/Bully-leader-election/blob/master/Failure%20causing%20ReElection.png "Failure causing ReElection")

ID value - process name mapping-
1. glados
2. california
3. rhea
4. newyork
5. maine

The current leader is maine(5). while everyone is sending a heartbeat to the leader, glados(1) realizes that the leader has failed and starts an election to appoint a new leader. while the election is in progress, newyork(4) and rhea(3) fail as well. after a timeout, california(2) realizes this and starts a ReElection to appoint a new leader. since it gets no replies from anyone, it proclaims itself to be the new leader and sends out a coordinator message to glados(1) which is currently the only process alive. glados(1) can then be seen sending a heartbeat messages to california.
________________________________________________________________________________
