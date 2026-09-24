package sync;

import api.Json;
import api.NetworkClient;
import api.Json;

import java.util.LinkedHashMap;
import java.util.Map;

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
    private final Map<String, Integer> scoreboard = new HashMap<>();

    private boolean wantsToUpdateScore = false;
    private volatile int currentTokenHolder = -1;

    private final Map<String, Integer> scores = new LinkedHashMap<>();

    public MutualExclusion(int nodeId, int nextPeerPort, boolean startsWithToken, NetworkClient networkClient) {
        this.nodeId = nodeId;
        this.nextPeerPort = nextPeerPort;
        this.currentTokenHolder = startsWithToken ? nodeId : -1;
        this.networkClient = networkClient;
    }

    public void requestCriticalSection() {
        boolean shouldPassToken;
        synchronized (this) {
            wantsToUpdateScore = true;
            shouldPassToken = hasToken;
            if (shouldPassToken) {
                wantsToUpdateScore = false;
                // Execute the critical section while holding the token.
            }
        }

        if (shouldPassToken) {
            passToken();
        }
    }

    public void receiveToken() {
        synchronized (this) {
            this.hasToken = true;

            if (wantsToUpdateScore) {
                // Execute the critical section while holding the token.
                wantsToUpdateScore = false;
            }
        }

        passToken();
}

    private void passToken() {
        String payload;
        synchronized (this) {
            if (!hasToken) {
                return;
            }

            Map<String, Object> token = new LinkedHashMap<>();
            token.put("token_holder", nodeId);
            token.put("scores", new LinkedHashMap<>(scores));
            payload = Json.stringify(token);
            hasToken = false;
        }

        // post() is asynchronous; do not hold this object's monitor across it.
        networkClient.post(nextPeerPort, "/api/token", payload);
    }
}

