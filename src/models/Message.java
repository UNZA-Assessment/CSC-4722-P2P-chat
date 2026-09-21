package models;

/**
 * Owned by Pair B. Signature frozen in PROTOCOL.md - other pairs may
 * construct/read this class, so don't change the constructor or field
 * names without going through A1.
 */
public class Message {
    public final int senderId;
    public final String text;
    public final int lamportTime;
    public final int[] vectorClock;
    public final long receivedAtNanos;

    public Message(int senderId, String text, int lamportTime, int[] vectorClock) {
        this.senderId = senderId;
        this.text = text;
        this.lamportTime = lamportTime;
        this.vectorClock = vectorClock;
        this.receivedAtNanos = System.nanoTime();
    }

    // TODO (Pair B): total-order comparator: (lamportTime, senderId) ascending
    // TODO (Pair B): causal helpers - happensBefore(Message other),
    //                isConcurrentWith(Message other) using vectorClock
}
