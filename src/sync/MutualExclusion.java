package sync;

import api.NetworkClient;

/**
 * Owned by Pair C. TODO items are the assignment's Mutual Exclusion &
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

    public void receiveToken() {

    synchronized (this) {
        hasToken = true;

        System.out.println("Node " + nodeId + " received the token.");

        if (wantsToUpdateScore) {

            // TODO: Update shared scoreboard here.
            System.out.println(
                    "Node " + nodeId + " ENTERING critical section.");

            // Scoreboard update goes here

            System.out.println(
                    "Node " + nodeId + " EXITING critical section.");

            wantsToUpdateScore = false;
        }

        // Token is being passed on.
        hasToken = false;
    }

    // IMPORTANT:
    // This happens AFTER synchronized block has released the lock.
    passToken();
}


   private void passToken() {

    String payload =
            "{\"token_holder\":" + nodeId + ",\"scores\":{}}";

    System.out.println(
            "Node " + nodeId +
            " passing token to port " + nextPeerPort);

    networkClient.post(
            nextPeerPort,
            "/api/token",
            payload);
}

}
