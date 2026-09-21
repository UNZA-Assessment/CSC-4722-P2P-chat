package sync;

import api.NetworkClient;

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

    private boolean wantsToUpdateScore = false;
    private boolean hasToken = false;

    public MutualExclusion(int nodeId, int nextPeerPort, boolean startsWithToken, NetworkClient networkClient) {
        this.nodeId = nodeId;
        this.nextPeerPort = nextPeerPort;
        this.hasToken = startsWithToken;
        this.networkClient = networkClient;
    }

    public synchronized void requestCriticalSection() {
        this.wantsToUpdateScore = true;
    }

    public synchronized void receiveToken() {
        hasToken = true;
        if (wantsToUpdateScore) {
            // TODO (Pair D): execute critical section - update shared scoreboard.
            // Log entry/exit timestamps here; this is your proof of mutual
            // exclusion for the report.
            wantsToUpdateScore = false;
        }
        passToken();
    }

    private void passToken() {
        // TODO (Pair D): build the {"token_holder":..,"scores":{...}} payload
        //                with api.Json.stringify(...)
        // TODO (Pair D): networkClient.post(nextPeerPort, "/api/token", payload)
        //                - this is async, do not block here
        // TODO (Pair D): before sending, consider probing networkClient
        //                .getHealth(nextPeerPort) and walking forward past
        //                dead peers (ring repair) - see the assignment brief
        // TODO (Pair D): set hasToken = false once the send is issued
    }
}
