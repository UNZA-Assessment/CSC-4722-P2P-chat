package sync;

import api.Json;
import api.NetworkClient;
import models.Clock;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owned by Pair D. TODO items are the assignment's Mutual Exclusion &
 * Token Ring requirement (25 marks).
 *
 * IMPORTANT bug in the original skeleton that this scaffold sets you up
 * to avoid: receiveToken() below is synchronized, and if passToken() does
 * a BLOCKING send while still inside that lock, a small ring can deadlock
 * (this node's own synchronized call can end up waiting on a chain that
 * loops back to it). Use networkClient.post(...) (async) for the token
 * send, and release the lock before or during the send - don't hold the
 * monitor across the network call.
 */
public class MutualExclusion {

    private final int nodeId;
    private final int nextPeerPort;
    private final NetworkClient networkClient;
    private final Clock clock;
    private final Map<String, Integer> scoreboard = new HashMap<>();

    private int pendingScoreUpdates;
    private boolean hasToken;
    private volatile int currentTokenHolder = -1;

    public MutualExclusion(int nodeId, int nextPeerPort, boolean startsWithToken, NetworkClient networkClient) {
        this(nodeId, nextPeerPort, startsWithToken, networkClient, null);
    }

    public MutualExclusion(int nodeId, int nextPeerPort, boolean startsWithToken,
                           NetworkClient networkClient, Clock clock) {
        this.nodeId = nodeId;
        this.nextPeerPort = nextPeerPort;
        this.hasToken = startsWithToken;
        this.currentTokenHolder = startsWithToken ? nodeId : -1;
        this.networkClient = networkClient;
        this.clock = clock;
    }

    public void requestCriticalSection() {
        Map<String, Integer> scoresToForward = null;
        synchronized (this) {
            pendingScoreUpdates++;
            if (hasToken) {
                scoresToForward = consumeTokenLocked();
            }
        }
        if (scoresToForward != null) {
            passToken(scoresToForward);
        }
    }

    public void receiveToken() {
        receiveToken(new HashMap<>(), 0, null);
    }

    public void receiveToken(Map<String, Integer> incomingScores) {
        receiveToken(incomingScores, 0, null);
    }

    public void receiveToken(Map<String, Integer> incomingScores,
                             int incomingLamport, int[] incomingVector) {
        Map<String, Integer> scoresToForward;
        synchronized (this) {
            hasToken = true;
            currentTokenHolder = nodeId;

            if (clock != null && (incomingLamport > 0 || incomingVector != null)) {
                clock.updateOnReceive(incomingLamport, incomingVector);
            }

            if (incomingScores != null) {
                for (Map.Entry<String, Integer> entry : incomingScores.entrySet()) {
                    scoreboard.merge(entry.getKey(), entry.getValue(), Math::max);
                }
            }

            System.out.println("Node " + nodeId + " received the token.");
            scoresToForward = consumeTokenLocked();
        }

        passToken(scoresToForward);
    }

    public int getCurrentTokenHolder() {
        return currentTokenHolder;
    }

    public synchronized Map<String, Integer> getScoreboard() {
        return new HashMap<>(scoreboard);
    }

    public int getLamportTime() {
        return clock == null ? 0 : clock.getLamportTime();
    }

    public int[] getVectorClock() {
        return clock == null ? new int[0] : clock.getVectorClock();
    }

    private void passToken(Map<String, Integer> scores) {
        if (clock != null) {
            clock.tick();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token_holder", nodeId);
        payload.put("scores", scores);
        if (clock != null) {
            payload.put("lamport", clock.getLamportTime());
            payload.put("vector", clock.getVectorClock());
        }

        System.out.println("Node " + nodeId + " passing token to port " + nextPeerPort);

        networkClient.post(nextPeerPort, "/api/token", Json.stringify(payload));
    }

    private Map<String, Integer> consumeTokenLocked() {
        if (pendingScoreUpdates > 0) {
            System.out.println("Node " + nodeId + " ENTERING critical section.");
            scoreboard.merge("node_" + nodeId, pendingScoreUpdates, Integer::sum);
            System.out.println("Node " + nodeId + " EXITING critical section.");
            pendingScoreUpdates = 0;
        }
        hasToken = false;
        currentTokenHolder = -1;
        return new HashMap<>(scoreboard);
    }
}

