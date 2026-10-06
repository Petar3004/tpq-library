package Utils;

import static Utils.Utils.IntervalPair;
import static Utils.Utils.safeAdd;
import static Utils.Utils.safeSub;

public record Interval(int lBound, int uBound) {

    /**
     * Creates a closed integer interval [lBound, uBound].
     *
     * @throws IllegalArgumentException if the lower bound exceeds the upper bound
     */
    public Interval {
        if (uBound < lBound) {
            throw new IllegalArgumentException("Invalid interval");
        }
    }

    /**
     * Computes the Minkowski sum of two intervals.
     *
     * For A = [a1, a2] and B = [b1, b2]:
     *
     *     A + B = [a1 + b1, a2 + b2]
     *
     * Null is treated as an absent interval.
     */
    public static Interval intAdd(Interval A, Interval B) {
        if (A == null && B == null) {
            return null;
        }

        if (A == null) {
            return B;
        }

        if (B == null) {
            return A;
        }

        return new Interval(
                safeAdd(A.lBound(), B.lBound()),
                safeAdd(A.uBound(), B.uBound())
        );
    }

    /**
     * Computes the interval difference A - B.
     *
     * For A = [a1, a2] and B = [b1, b2]:
     *
     *     A - B = [a1 - b2, a2 - b1]
     *
     * Null is treated as an absent interval.
     */
    public static Interval intSub(Interval A, Interval B) {
        if (A == null && B == null) {
            return null;
        }

        if (A == null) {
            return new Interval(
                    safeSub(0, B.uBound()),
                    safeSub(0, B.lBound())
            );
        }

        if (B == null) {
            return A;
        }

        return new Interval(
                safeSub(A.lBound(), B.uBound()),
                safeSub(A.uBound(), B.lBound())
        );
    }

    /**
     * Computes the set difference A \ B.
     *
     * Because the difference of two intervals may consist of two disjoint intervals, the result is represented as an IntervalPair.
     *
     * @return null if the difference is empty
     */
    public static IntervalPair intDiff(Interval A, Interval B) {
        if (A == null) {
            return null;
        }

        if (B == null) {
            return new IntervalPair(A, null);
        }

        // No overlap: all of A remains.
        if (!intersectionExists(A, B)) {
            return new IntervalPair(A, null);
        }

        // B completely covers A.
        if (B.lBound() <= A.lBound()
                && B.uBound() >= A.uBound()) {
            return null;
        }

        Interval left = null;
        Interval right = null;

        /*
         * Portion of A strictly before B.
         *
         * The condition guarantees B.lBound() > A.lBound(),
         * so the subtraction is safe unless B starts at negative infinity,
         * which cannot occur here.
         */
        if (B.lBound() > A.lBound()) {
            left = new Interval(
                    A.lBound(),
                    B.lBound() - 1
            );
        }

        /*
         * Portion of A strictly after B.
         *
         * The condition guarantees B.uBound() < A.uBound(),
         * so the addition is safe unless B ends at positive infinity,
         * which cannot occur here.
         */
        if (B.uBound() < A.uBound()) {
            right = new Interval(
                    B.uBound() + 1,
                    A.uBound()
            );
        }

        // The difference consists of two disjoint intervals.
        if (left != null && right != null) {
            return new IntervalPair(left, right);
        }

        // The difference consists of one interval.
        if (left != null) {
            return new IntervalPair(left, null);
        }

        if (right != null) {
            return new IntervalPair(right, null);
        }

        return null;
    }

    /**
     * Computes the union of two intervals when their union is itself a single interval.
     *
     * Overlapping or adjacent integer intervals may be merged.
     *
     * @return the merged interval, or null if the union is disconnected
     */
    public static Interval intUnion(Interval A, Interval B) {
        if (A == null && B == null) {
            return null;
        }

        if (A == null) {
            return B;
        }

        if (B == null) {
            return A;
        }

        if (!unionIsAnInterval(A, B)) {
            return null;
        }

        return new Interval(
                Math.min(A.lBound(), B.lBound()),
                Math.max(A.uBound(), B.uBound())
        );
    }

    /**
     * Computes the intersection of two intervals.
     *
     * @return the intersection, or null if the intervals are disjoint
     */
    public static Interval intIntersect(Interval A, Interval B) {
        if (A == null || B == null) {
            return null;
        }

        if (!intersectionExists(A, B)) {
            return null;
        }

        return new Interval(
                Math.max(A.lBound(), B.lBound()),
                Math.min(A.uBound(), B.uBound())
        );
    }

    /**
     * Tests whether interval A is a subset of interval B.
     *
     * Null is interpreted as the empty interval.
     */
    public static boolean isIntSubset(Interval A, Interval B) {
        if (A == null) {
            return true;
        }

        if (B == null) {
            return false;
        }

        return A.lBound() >= B.lBound() && A.uBound() <= B.uBound();
    }

    /**
     * Tests whether two intervals have a non-empty intersection.
     */
    public static boolean intersectionExists(Interval A, Interval B) {
        if (A == null || B == null) {
            return false;
        }

        return A.uBound() >= B.lBound() && B.uBound() >= A.lBound();
    }

    /**
     * Tests whether the union of two closed integer intervals is itself a single interval.
     *
     * Adjacent intervals are considered mergeable;
     * for example, [1, 3] and [4, 6] form the interval [1, 6].
     */
    public static boolean unionIsAnInterval(Interval A, Interval B) {
        if (A == null || B == null) {
            return false;
        }

        return A.uBound() + 1 >= B.lBound() && B.uBound() + 1 >= A.lBound();
    }
}