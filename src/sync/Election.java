package sync;

import api.Json;
import api.NetworkClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owned by Pair C. TODO items are the assignment's Bully Election
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

    private static final long ELECTION_TIMEOUT_MILLIS = 1000;
    private final int nodeId;
    private final List<Integer> peerPorts;
    private final NetworkClient networkClient;

    private int currentLeaderId;
    private boolean isElectionInProgress;
    private boolean higherNodeResponded;
    private long electionGeneration;

    public Election(int nodeId, List<Integer> peerPorts, NetworkClient networkClient) {
        this.nodeId = nodeId;
        this.peerPorts = new ArrayList<>(peerPorts);
        this.networkClient = networkClient;
        this.currentLeaderId = highestKnownNodeId();
    }

    public void startElection() {
        final long generation;
        synchronized (this) {
            if (isElectionInProgress) {
                return;
            }
            isElectionInProgress = true;
            higherNodeResponded = false;
            generation = ++electionGeneration;
        }

        System.out.println("Node " + nodeId + " starting election...");
        String payload = electionPayload("ELECTION", nodeId);
        for (int higherNodeId : higherNodeIds()) {
            networkClient.post(portForNode(higherNodeId), "/api/election", payload);
        }

        Thread timeoutThread = new Thread(
                () -> finishElectionAfterTimeout(generation),
                "election-timeout-" + nodeId + "-" + generation);
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    /** Replies immediately, then starts this node's election asynchronously. */
    public void handleElectionMessage(int senderId) {
        networkClient.post(portForNode(senderId), "/api/election",
                electionPayload("OK", nodeId));

        Thread electionThread = new Thread(this::startElection,
                "election-response-" + nodeId);
        electionThread.setDaemon(true);
        electionThread.start();
    }

    /** Records an OK from a higher-priority node for the active election. */
    public synchronized void handleOkMessage(int senderId) {
        if (senderId > nodeId && isElectionInProgress) {
            higherNodeResponded = true;
        }
    }

    public synchronized void handleCoordinatorMessage(int newLeaderId) {
        currentLeaderId = newLeaderId;
        isElectionInProgress = false;
        higherNodeResponded = false;
        electionGeneration++;
        System.out.println("New Leader recognized: Node " + newLeaderId);
    }

    private void finishElectionAfterTimeout(long generation) {
        try {
            Thread.sleep(ELECTION_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        synchronized (this) {
            if (generation != electionGeneration || !isElectionInProgress) {
                return;
            }
            if (higherNodeResponded) {
                isElectionInProgress = false;
                return;
            }
            currentLeaderId = nodeId;
            isElectionInProgress = false;
            higherNodeResponded = false;
        }

        broadcastCoordinator();
    }

    private void broadcastCoordinator() {
        String payload = electionPayload("COORDINATOR", nodeId);
        for (int peerNodeId : knownNodeIds()) {
            if (peerNodeId != nodeId) {
                networkClient.post(portForNode(peerNodeId), "/api/election", payload);
            }
        }
    }

    private List<Integer> higherNodeIds() {
        List<Integer> result = new ArrayList<>();
        for (int knownNodeId : knownNodeIds()) {
            if (knownNodeId > nodeId) {
                result.add(knownNodeId);
            }
        }
        return result;
    }

    private List<Integer> knownNodeIds() {
        List<Integer> nodeIds = new ArrayList<>();
        for (int index = 0; index < peerPorts.size(); index++) {
            nodeIds.add(index);
        }
        return nodeIds;
    }

    private int highestKnownNodeId() {
        return knownNodeIds().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(nodeId);
    }

    private int portForNode(int targetNodeId) {
        if (targetNodeId < 0 || targetNodeId >= peerPorts.size()) {
            throw new IllegalArgumentException("Unknown node ID: " + targetNodeId);
        }
        return peerPorts.get(targetNodeId);
    }

    private String electionPayload(String type, int senderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("sender_id", senderId);
        return Json.stringify(payload);
    }
}
