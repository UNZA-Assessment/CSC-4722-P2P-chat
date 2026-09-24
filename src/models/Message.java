package models;

import java.util.Comparator;

/**
 * Owned by Pair B. Signature frozen in PROTOCOL.md - other pairs may
 * construct/read this class, so don't change the constructor or field
 * names without going through A1.
 */
public class Message implements Comparable<Message> {
    public static final Comparator<Message> BY_TOTAL_ORDER = Comparator
            .comparingInt((Message message) -> message.lamportTime)
            .thenComparingInt(message -> message.senderId);

    public final int senderId;
    public final String text;
    public final int lamportTime;
    public final int[] vectorClock;
    public final long receivedAtNanos;

    public Message(int senderId, String text, int lamportTime, int[] vectorClock) {
        this.senderId = senderId;
        this.text = text;
        this.lamportTime = lamportTime;
        this.vectorClock = vectorClock == null ? new int[0] : vectorClock.clone();
        this.receivedAtNanos = System.nanoTime();
    }

    @Override
    public int compareTo(Message other) {
        return BY_TOTAL_ORDER.compare(this, other);
    }

    public boolean happensBefore(Message other) {
        if (other == null || this.vectorClock.length != other.vectorClock.length) {
            return false;
        }

        boolean hasStrictlyLess = false;
        for (int i = 0; i < this.vectorClock.length; i++) {
            if (this.vectorClock[i] > other.vectorClock[i]) {
                return false;
            }
            if (this.vectorClock[i] < other.vectorClock[i]) {
                hasStrictlyLess = true;
            }
        }
        return hasStrictlyLess;
    }

    public boolean isConcurrentWith(Message other) {
        if (other == null || this == other) {
            return false;
        }
        return !this.happensBefore(other) && !other.happensBefore(this);
    }
}
