# Quorum

## Experimental distributed consensus system on top of akka cluster


After watching [this video](https://www.youtube.com/watch?v=LAqyTyNUYSY) on Raft distributed consensus algorithm, I got an idea and decided, as a proof of concept,
to implement a distributed consensus prototype sitting on top of akka cluster, which supports cluster membership with gossip protocol underneath.

Distributed consensus system can provide scalable, fault tolerant services using multiple replicated data servers in a cluster.
From a client's perspective, the distributed system is just a single consistent system functioning regardless of node or link failures.

In this experimental implementation, the state of each replica server in a cluster system is one of the three states:
`Leading`, `Following`, or `Not-in-Service`, as oppose to `Leader`, `Follower`, or `Candidate` in the original Raft algorithm.

Leading state makes transition to an extra state `CommandProcessing` when handling an agent's `Command` request.

![states](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/Capture1.PNG)

All the replica servers in the minor group enter NIS state until the group become major. Once the group of replica servers forms major,
each replica server of the group enters the state of either `leading` or `following`, depending on the node's role of leader or not, maintained by akka cluster.

In case link failure, thus the cluster splits like picture below, all replica servers of minor split enter `NIS` state, while relica servers in major split keep functioning normaly. Replicas in `NIS` do not handle clients' requests at all.

![splits](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/Capture.PNG)

Clients place `put` or `get` reuqests to the consensus cluster system. Clients request them via a local agent which remote access to the cluster system. Agent handles client's put request by sending `Command` message to cluster, and get request by sending `Query` message.
Agent sends `Command` or `Query` to one of the replica servers in the cluster which was chosen by the quickest `PONG` response to broadcasted `PING` message.

![pingpong](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/Capture3.PNG)

Below is the message sequence diagram between the agent and the cluster interaction, in case of handling `Command` message from agent.

![sequence diagram](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/Capture4.PNG)


###How to run and observe the agent and the cluster in action on IntelliJ IDEA in your laptop:

* Import the project into your IntelliJ IDEA

* Start seed replica on port 2553. This will create single node cluster with one replica state `NIS`.
![serverstart3](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/s3.PNG)

* Be sure that starting sequence is StartServer3 -> StartServer2 -> StartServer1

* After ServerStart3 was fully started, start second replica on port 2552. You should see replica 2553 state transition to `following`, 2552 state transition to `leading`.
![serverstart2](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/s2.PNG)
![log2](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/22.PNG)

* Start third replica on port 2551 the same way. You should see replica 2553 state stays `following`, 2552 state transition to `following`, 2551 state transition to `leading`.
![startserver1](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/s1.PNG)
![log1](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/11.PNG)

* Start agent and watch agent console output simulating client.
![agent](https://raw.githubusercontent.com/sangche/sangche.github.io/master/pics/050218/agnt.PNG)

* Observe log INFO messages of each replicas.

* Try stop and restart replica servers(except seed replica on port 2553) and watch agent output and replica servers' INFO log.

* Change number of replicas in the cluster by changing `nodes` attribute in application.conf for your own configuration.

Note: INFO messages should be DEBUG messages, but it's intentional to avoid too many
DEBUG messages output from cluster system.

Note: This is quick 'proof of idea' initial implementation and so lots of potential bugs are expected.