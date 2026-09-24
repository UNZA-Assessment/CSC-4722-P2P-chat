package api;

import models.Clock;

import java.util.Arrays;

/**
 * Shared logger for the project-wide event format.
 *
 * Format: [nodeId] LAMPORT=<n> VECTOR=<[...]> <EVENT_TAG> <free text>
 */
public final class Log {
    private Log() {
    }

    public static void event(int nodeId, Clock clock, String eventTag, String message) {
        int lamport = clock == null ? 0 : clock.getLamportTime();
        int[] vector = clock == null ? new int[0] : clock.getVectorClock();
        String vectorText = Arrays.toString(vector);
        String formattedMessage = message == null ? "" : message;
        System.out.println("[" + nodeId + "] LAMPORT=" + lamport + " VECTOR=" + vectorText + " "
                + eventTag + " " + formattedMessage);
    }
}
