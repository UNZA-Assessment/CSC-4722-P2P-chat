package sync;

import api.NetworkClient;

import java.util.List;

/**
 * Owned by Pair D. TODO items are the assignment's Bully Election
 * requirement (25 marks).
 *
 * Two bugs in the original skeleton this scaffold sets you up to fix:
 *  1. currentLeaderId was set to peerPorts.size() - 1, which silently
 *     assumes node IDs equal list indices and breaks the moment you demo
 *     with a subset of nodes. Use the max known node ID instead.
 *  2. handleElectionMessage must reply OK immediately and then run its
 *     own election check on a SEPARATE thread - do not run startElection()
 *     synchronously inside the HTTP handler thread, or you block the
 *     response and can deadlock a small cluster.
 */
public class Election {

    private final int nodeId;
    private final List<Integer> peerPorts;
    private final NetworkClient networkClient;

    private int currentLeaderId;
    private boolean isElectionInProgress = false;

    public Election(int nodeId, List<Integer> peerPorts, NetworkClient networkClient) {
        this.nodeId = nodeId;
        this.peerPorts = peerPorts;
        this.networkClient = networkClient;
        // TODO (Pair D): replace with max node ID actually known, not size()-1
        this.currentLeaderId = peerPorts.size() - 1;
    }

    public void startElection() {
        System.out.println("Node " + nodeId + " starting election...");
        isElectionInProgress = true;
        // TODO (Pair D): send {"type":"ELECTION","sender_id":nodeId} to all
        //                peers with higher node IDs via networkClient.post
        // TODO (Pair D): if no higher node replies within a timeout,
        //                declare self leader
        // TODO (Pair D): if self wins, broadcast
        //                {"type":"COORDINATOR","sender_id":nodeId} to all peers
    }

    public void handleElectionMessage(int senderId) {
        // TODO (Pair D): send {"type":"OK","sender_id":nodeId} back to senderId
        //                BEFORE doing anything else
        // TODO (Pair D): if not already in an election, start one on a new
        //                thread - do not call startElection() inline here
    }

    public void handleCoordinatorMessage(int newLeaderId) {
        this.currentLeaderId = newLeaderId;
        this.isElectionInProgress = false;
        System.out.println("New Leader recognized: Node " + newLeaderId);
    }

    // TODO (Pair D): background failure detector - poll
    //                networkClient.getHealth(portOf(currentLeaderId)) on an
    //                interval; after N consecutive failures, call
    //                startElection(). Run this on its own thread from Node.java.
}
