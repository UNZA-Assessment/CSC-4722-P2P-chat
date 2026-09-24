package sync;

import api.NetworkClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owned by Pair D. Implements the Bully election protocol and a simple
 * leader-health monitor.
 */
public class Election {

    private static final long ELECTION_TIMEOUT_MS = 800L;
    private static final int MAX_CONSECUTIVE_FAILURES = 2;

    private final int nodeId;
    private final List<Integer> peerPorts;
    private final NetworkClient networkClient;

    private int currentLeaderId;
    private volatile boolean isElectionInProgress = false;

    public Election(int nodeId, List<Integer> peerPorts, NetworkClient networkClient) {
        this.nodeId = nodeId;
        this.peerPorts = peerPorts;
        this.networkClient = networkClient;
        this.currentLeaderId = maxKnownNodeId();
    }

    private int maxKnownNodeId() {
        int maxNodeId = nodeId;
        for (int i = 0; i < peerPorts.size(); i++) {
            maxNodeId = Math.max(maxNodeId, i);
        }
        return maxNodeId;
    }

    private int portOf(int targetNodeId) {
        if (targetNodeId < 0 || targetNodeId >= peerPorts.size()) {
            return -1;
        }
        return peerPorts.get(targetNodeId);
    }

    public void startElection() {
        synchronized (this) {
            if (isElectionInProgress) {
                return;
            }
            isElectionInProgress = true;
        }

        System.out.println("Node " + nodeId + " starting election...");

        List<Integer> higherNodes = new ArrayList<>();
        for (int candidateId = nodeId + 1; candidateId < peerPorts.size(); candidateId++) {
            if (portOf(candidateId) >= 0) {
                higherNodes.add(candidateId);
            }
        }

        CountDownLatch replyLatch = new CountDownLatch(1);
        AtomicBoolean sawHigherNodeReply = new AtomicBoolean(false);

        for (int higherNodeId : higherNodes) {
            int port = portOf(higherNodeId);
            if (port < 0) {
                continue;
            }

            networkClient.post(port, "/api/election",
                    "{\"type\":\"ELECTION\",\"sender_id\":" + nodeId + "}")
                    .thenAccept(statusCode -> {
                        if (statusCode == 200) {
                            sawHigherNodeReply.set(true);
                            replyLatch.countDown();
                        }
                    });
        }

        try {
            boolean gotReply = replyLatch.await(ELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!gotReply || !sawHigherNodeReply.get()) {
                currentLeaderId = nodeId;
                broadcastCoordinator();
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (this) {
            isElectionInProgress = false;
        }
    }

    private void broadcastCoordinator() {
        for (int peerId = 0; peerId < peerPorts.size(); peerId++) {
            if (peerId == nodeId) {
                continue;
            }
            int peerPort = portOf(peerId);
            if (peerPort < 0) {
                continue;
            }
            networkClient.post(peerPort, "/api/election",
                    "{\"type\":\"COORDINATOR\",\"sender_id\":" + nodeId + "}");
        }
        synchronized (this) {
            isElectionInProgress = false;
        }
        System.out.println("Node " + nodeId + " is now leader.");
    }

    public void handleElectionMessage(int senderId) {
        int senderPort = portOf(senderId);
        if (senderPort >= 0) {
            networkClient.post(senderPort, "/api/election",
                    "{\"type\":\"OK\",\"sender_id\":" + nodeId + "}");
        }

        synchronized (this) {
            if (isElectionInProgress) {
                return;
            }
            isElectionInProgress = true;
        }

        Thread electionThread = new Thread(this::startElection, "election-" + nodeId);
        electionThread.setDaemon(true);
        electionThread.start();
    }

    public void handleCoordinatorMessage(int newLeaderId) {
        this.currentLeaderId = newLeaderId;
        synchronized (this) {
            this.isElectionInProgress = false;
        }
        System.out.println("Node " + nodeId + " recognized leader: Node " + newLeaderId);
    }

    public int getCurrentLeaderId() {
        return currentLeaderId;
    }

    public int getLeaderPort() {
        return portOf(currentLeaderId);
    }

    public boolean isElectionInProgress() {
        return isElectionInProgress;
    }

    public void startFailureDetector() {
        Thread detector = new Thread(() -> {
            int consecutiveFailures = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (currentLeaderId == nodeId) {
                    continue;
                }

                int leaderPort = portOf(currentLeaderId);
                if (leaderPort < 0) {
                    continue;
                }

                boolean healthy = networkClient.getHealth(leaderPort);
                if (healthy) {
                    consecutiveFailures = 0;
                } else {
                    consecutiveFailures++;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        consecutiveFailures = 0;
                        if (!isElectionInProgress) {
                            System.out.println("Node " + nodeId + " detected leader failure on node " + currentLeaderId + "; starting election.");
                            startElection();
                        }
                    }
                }
            }
        }, "failure-detector-" + nodeId);
        detector.setDaemon(true);
        detector.start();
    }
}
