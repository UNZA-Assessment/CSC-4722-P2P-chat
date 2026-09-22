package models;

import java.util.Arrays;

/**
 * Small executable check for Clock without a test framework.
 * Run with: java -cp out models.ClockTestHarness
 */
public final class ClockTestHarness {
    private ClockTestHarness() { }

    public static void main(String[] args) {
        Clock clock = new Clock(1, 3);

        clock.tick();
        expect("local tick Lamport", 1, clock.getLamportTime());
        expectVector("local tick vector", new int[] {0, 1, 0}, clock.getVectorClock());

        clock.updateOnReceive(5, new int[] {2, 3, 4});
        expect("receive Lamport", 6, clock.getLamportTime());
        expectVector("receive merge and one local increment",
                new int[] {2, 4, 4}, clock.getVectorClock());

        clock.updateOnReceive(2, new int[] {9, 0, 1});
        expect("lower incoming Lamport still increments once", 7, clock.getLamportTime());
        expectVector("second receive merge and one local increment",
                new int[] {9, 5, 4}, clock.getVectorClock());

        System.out.println("ClockTestHarness: PASS");
    }

    private static void expect(String description, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expectVector(String description, int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + ": expected "
                    + Arrays.toString(expected) + ", got " + Arrays.toString(actual));
        }
    }
}