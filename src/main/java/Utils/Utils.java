package Utils;

import Data.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Utils {

    public static final int POS_INF = Integer.MAX_VALUE;
    public static final int NEG_INF = Integer.MIN_VALUE;

    // Interval containing all representable temporal values.
    public static final Interval FULL_INT = new Interval(NEG_INF, POS_INF);

    /**
     * Associates a node with a list of temporal windows.
     */
    public record NodeListPair(String n, List<Window> l) {}

    /**
     * Represents a result consisting of up to two disjoint intervals.
     */
    public record IntervalPair(Interval i1, Interval i2) {}

    /**
     * Associates a flight identifier with a node pair.
     */
    public record FlightKey(int flightNum, NodePair nodes) {}

    /**
     * Represents an ordered pair of graph nodes.
     */
    public record NodePair(String n1, String n2) {

        public void print() {
            System.out.println(n1 + " --> " + n2);
        }
    }

    /**
     * Identifies which component of a temporal window is being processed.
     */
    public enum IntervalType {
        SIGMA,
        DELTA,
        TAU,
        ALL
    }

    /**
     * Runtime categories recorded during query evaluation.
     */
    public enum TimeMetric {
        JOIN,
        COALESCE,
        NORMALIZE
    }

    /**
     * Count-based metrics recorded during query evaluation.
     */
    public enum CountMetric {
        NORMALIZED_WINDOWS,
        MAX_WINDOWS
    }

    /**
     * Adds two integer values while respecting the sentinel representation of positive and negative infinity.
     *
     * Finite arithmetic that exceeds the integer range is saturated to the corresponding infinity sentinel.
     *
     * @throws ArithmeticException if +inf and -inf are added
     */
    public static int safeAdd(int x, int y) {
        // +inf + -inf and -inf + +inf are undefined.
        if ((x == POS_INF && y == NEG_INF) || (x == NEG_INF && y == POS_INF)) {
            throw new ArithmeticException(
                    "Undefined addition of positive and negative infinity"
            );
        }

        if (x == POS_INF || y == POS_INF) {
            return POS_INF;
        }

        if (x == NEG_INF || y == NEG_INF) {
            return NEG_INF;
        }

        // Use long arithmetic to detect ordinary integer overflow.
        long result = (long) x + y;

        if (result >= POS_INF) {
            return POS_INF;
        }

        if (result <= NEG_INF) {
            return NEG_INF;
        }

        return (int) result;
    }

    /**
     * Subtracts two integer values while respecting the sentinel representation of positive and negative infinity.
     *
     * Finite arithmetic that exceeds the integer range is saturated to the corresponding infinity sentinel.
     *
     * @throws ArithmeticException for +inf - +inf or -inf - -inf
     */
    public static int safeSub(int x, int y) {
        // Subtracting equal infinities is undefined.
        if ((x == POS_INF && y == POS_INF) || (x == NEG_INF && y == NEG_INF)) {
            throw new ArithmeticException(
                    "Undefined subtraction of equal infinities"
            );
        }

        // +inf - finite and +inf - (-inf).
        if (x == POS_INF || y == NEG_INF) {
            return POS_INF;
        }

        // -inf - finite and finite - +inf.
        if (x == NEG_INF || y == POS_INF) {
            return NEG_INF;
        }

        // Use long arithmetic to detect ordinary integer overflow.
        long result = (long) x - y;

        if (result >= POS_INF) {
            return POS_INF;
        }

        if (result <= NEG_INF) {
            return NEG_INF;
        }

        return (int) result;
    }

    /**
     * Coalesces overlapping or adjacent closed integer intervals.
     *
     * For example:
     *
     *     [1, 3], [4, 7], [10, 12]
     *
     * becomes:
     *
     *     [1, 7], [10, 12]
     *
     * The input list itself is not reordered or modified.
     *
     * @param intervals intervals to coalesce
     * @return a new list containing the coalesced intervals
     */
    public static List<Interval> coalesce(List<Interval> intervals) {
        if (intervals.size() <= 1) {
            return new ArrayList<>(intervals);
        }

        // Sort a copy so the caller's list is not modified.
        List<Interval> sorted = new ArrayList<>(intervals);

        sorted.sort(Comparator.comparingInt(Interval::lBound));

        List<Interval> merged = new ArrayList<>();

        for (Interval current : sorted) {
            if (merged.isEmpty()) {
                merged.add(current);
                continue;
            }

            int lastIndex = merged.size() - 1;
            Interval previous = merged.get(lastIndex);

            /*
             * If the intervals are disjoint and non-adjacent, start a new merged interval.
             */
            if (!Interval.unionIsAnInterval(previous, current)) {
                merged.add(current);
                continue;
            }

            /*
             * Otherwise merge the current interval with the previous one.
             *
             * Keep the previous lower bound;
             * because the intervals are sorted, it is guaranteed to be less than or equal to current.lBound().
             */
            Interval combined = new Interval(
                    previous.lBound(),
                    Math.max(
                            previous.uBound(),
                            current.uBound()
                    )
            );

            merged.set(lastIndex, combined);
        }

        return merged;
    }

    /**
     * Converts intervals into unary temporal windows.
     *
     * Each interval i becomes:
     *
     *     sigma = i
     *     delta = [0, 0]
     *     tau   = i
     *
     * @param intervals intervals to convert
     * @return corresponding list of unary windows
     */
    public static List<Window> mapIntervalsToWindows(List<Interval> intervals) {
        List<Window> windows = new ArrayList<>();

        for (Interval interval : intervals) {
            windows.add(
                    new Window(
                            interval,
                            new Interval(0, 0),
                            interval
                    )
            );
        }

        return windows;
    }
}
