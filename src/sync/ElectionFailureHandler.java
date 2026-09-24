package sync;

import api.NetworkClient;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone leader-failure handler matching the bully-election workflow.
 */
public class ElectionFailureHandler {

    private static final int FAILURE_THRESHOLD = 2;

    private final Election election;
    private final NetworkClient networkClient;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public ElectionFailureHandler(Election election, NetworkClient networkClient) {
        this.election = election;
        this.networkClient = networkClient;
    }

    public void checkLeaderHealth() {
        if (election == null || networkClient == null) {
            return;
        }

        if (election.isElectionInProgress()) {
            return;
        }

        int leaderPort = election.getLeaderPort();
        if (leaderPort < 0) {
            if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
                consecutiveFailures.set(0);
                election.startElection();
            }
            return;
        }

        if (!networkClient.getHealth(leaderPort)) {
            if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
                consecutiveFailures.set(0);
                election.startElection();
            }
        } else {
            consecutiveFailures.set(0);
        }
    }
}
