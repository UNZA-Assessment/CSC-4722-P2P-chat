package models;

import java.util.Arrays;

/**
 * Owned by Pair B. TODO items below are the assignment's core Logical
 * Clocks requirement (25 marks) - see PROTOCOL.md for the merge rule
 * (Lamport: max(local, incoming) + 1; Vector: componentwise max, then
 * increment own index once).
 */
public class Clock {
    // Mwape: updateOnReceive merge logic and the JUnit-free Clock test harness.
    // Enoch: review the Clock-only local tick, constructor/index assumptions,
    // and accessor behavior; keep receive merging separate from tick().
    private int lamportTime = 0;
    private final int[] vectorClock;
    private final int nodeId;

    public Clock(int nodeId, int totalNodes) {
        if (nodeId < 0 || nodeId >= totalNodes) {
            throw new IllegalArgumentException("nodeId must be in [0, totalNodes)");
        }
        this.nodeId = nodeId;
        this.vectorClock = new int[totalNodes];
    }

    // Local event tick
    public synchronized void tick() {
        lamportTime++;
        vectorClock[nodeId]++;
    }

    // Update clocks upon receiving a message
    public synchronized void updateOnReceive(int incomingLamport, int[] incomingVector) {
        lamportTime = Math.max(lamportTime, incomingLamport) + 1;
        for (int i = 0; i < vectorClock.length; i++) {
            vectorClock[i] = Math.max(vectorClock[i], incomingVector[i]);
        }
        vectorClock[nodeId]++;
    }

    public synchronized int getLamportTime() { return lamportTime; }

    public synchronized int[] getVectorClock() {
        return Arrays.copyOf(vectorClock, vectorClock.length);
    }
}
