package Data;

import Utils.Interval;

import java.util.List;
import java.util.Objects;

import static Utils.Interval.*;
import static Utils.Utils.*;

public class Window {

    /**
     * Temporal window components:
     * sigma describes the start times,
     * delta describes the durations,
     * tau describes the end times.
    */
    public Interval sigma = new Interval(NEG_INF, POS_INF);
    public Interval delta = new Interval(0, 0);
    public Interval tau = new Interval(NEG_INF, POS_INF);

    /**
     * Creates the identity window with delta = [0, 0].
     */
    public Window() {}

    /**
     * Creates a window from its three interval components.
     *
     * @param sigma interval containing start times
     * @param delta interval containing the difference t - s
     * @param tau   interval containing end times
     */
    public Window(Interval sigma, Interval delta, Interval tau) {
        if (sigma == null || delta == null || tau == null) {
            throw new IllegalArgumentException("Invalid intervals");
        }

        this.sigma = sigma;
        this.delta = delta;
        this.tau = tau;
    }

    /**
     * Constructs a point window from a single interval i = [s, t].
     *
     * The resulting window fixes:
     * sigma = [s, s],
     * delta = [t - s, t - s],
     * tau   = [t, t].
     */
    public Window(Interval i) {
        sigma = new Interval(i.lBound(), i.lBound());
        delta = new Interval(
                i.uBound() - i.lBound(),
                i.uBound() - i.lBound()
        );
        tau = new Interval(i.uBound(), i.uBound());
    }

    /**
     * Normalizes one or all components of the temporal window.
     *
     * @param intType component(s) to normalize
     * @return this window after normalization, or null if the constraints become inconsistent
     */
    public Window normalize(IntervalType intType) {
        switch (intType) {
            case SIGMA ->
                    sigma = intIntersect(intSub(tau, delta), sigma);

            case DELTA ->
                    delta = intIntersect(intSub(tau, sigma), delta);

            case TAU ->
                    tau = intIntersect(intAdd(sigma, delta), tau);

            case ALL -> {
                /*
                 * Compute all three updated intervals from the original window before replacing any of them.
                 */
                Interval sigmaP = intIntersect(intSub(tau, delta), sigma);
                Interval deltaP = intIntersect(intSub(tau, sigma), delta);
                Interval tauP = intIntersect(intAdd(sigma, delta), tau);

                sigma = sigmaP;
                delta = deltaP;
                tau = tauP;
            }
        }

        // A null interval indicates that the constraints are inconsistent.
        if (sigma == null || delta == null || tau == null) {
            return null;
        }

        return this;
    }

    /**
     * Attempts to coalesce two temporal windows.
     *
     * The windows are merged only when their union can be represented exactly by another single window.
     *
     * @return the merged window, or null if the windows are not coalescable
     */
    public static Window coalesce(Window w1, Window w2) {
        if (!areCoalescable(w1, w2)) {
            return null;
        }

        Interval newSigma = intUnion(w1.sigma, w2.sigma);
        Interval newDelta = intUnion(w1.delta, w2.delta);
        Interval newTau = intUnion(w1.tau, w2.tau);

        if (newSigma == null || newDelta == null || newTau == null) {
            return null;
        }

        return new Window(newSigma, newDelta, newTau);
    }

    /**
     * Repeatedly coalesces compatible windows in the supplied list.
     *
     * Whenever a merge succeeds, scanning restarts because the newly
     * enlarged window may now be coalescable with earlier entries.
     */
    public static void coalesceList(List<Window> list) {
        for (int i = 0; i < list.size(); i++) {
            Window current = list.get(i);

            for (int j = 0; j < list.size();) {
                if (i == j) {
                    j++;
                    continue;
                }

                Window merged = coalesce(current, list.get(j));

                if (merged != null) {
                    list.remove(j);
                    current = merged;

                    /*
                     * If an element before the current index was removed, the current element shifts one position to the left.
                     */
                    if (j < i) {
                        i--;
                    }

                    list.set(i, current);

                    /*
                     * Restart scanning because the merged window may now merge with a window that was checked earlier.
                     */
                    j = 0;
                } else {
                    j++;
                }
            }
        }
    }

    /**
     * Tests whether two windows can be represented exactly by a single coalesced window.
     *
     * Each component must have an interval-valued union, and both directional coalescability conditions must hold.
     */
    private static boolean areCoalescable(Window w1, Window w2) {
        return unionIsAnInterval(w1.sigma, w2.sigma)
                && unionIsAnInterval(w1.delta, w2.delta)
                && unionIsAnInterval(w1.tau, w2.tau)
                && conditionHolds(
                CoalescabilityCondition.ONE,
                w1.sigma,
                w2.sigma,
                w1.delta,
                w2.delta,
                w1.tau,
                w2.tau
        )
                && conditionHolds(
                CoalescabilityCondition.TWO,
                w1.sigma,
                w2.sigma,
                w1.delta,
                w2.delta,
                w1.tau,
                w2.tau
        );
    }

    /**
     * Checks one directional coalescability condition.
     *
     * Condition ONE considers sigma1 \ sigma2, while condition TWO considers sigma2 \ sigma1.
     * This ensures that the merged window does not introduce temporal points that were absent from both originals.
     */
    private static boolean conditionHolds(
            CoalescabilityCondition cc,
            Interval sigma1,
            Interval sigma2,
            Interval delta1,
            Interval delta2,
            Interval tau1,
            Interval tau2
    ) {
        IntervalPair sigmaDiff;
        Interval deltaUnion = intUnion(delta1, delta2);

        Interval intersectTau;
        Interval subsetTau;

        switch (cc) {
            case ONE -> {
                sigmaDiff = intDiff(sigma1, sigma2);
                intersectTau = tau2;
                subsetTau = tau1;
            }

            case TWO -> {
                sigmaDiff = intDiff(sigma2, sigma1);
                intersectTau = tau1;
                subsetTau = tau2;
            }

            default -> {
                System.out.println("Invalid condition index.");
                return false;
            }
        }

        // Empty difference: the condition holds trivially.
        if (sigmaDiff == null) {
            return true;
        }

        // The difference consists of a single interval.
        if (sigmaDiff.i2() == null) {
            return conditionHoldsAuxiliary(
                    sigmaDiff.i1(),
                    deltaUnion,
                    intersectTau,
                    subsetTau
            );
        }

        // The difference consists of two disjoint intervals.
        if (sigmaDiff.i1() != null) {
            return conditionHoldsAuxiliary(
                    sigmaDiff.i1(),
                    deltaUnion,
                    intersectTau,
                    subsetTau
            ) && conditionHoldsAuxiliary(
                    sigmaDiff.i2(),
                    deltaUnion,
                    intersectTau,
                    subsetTau
            );
        }

        return false;
    }

    /**
     * Checks whether the temporal points introduced by extending sigma over
     * sigmaDiff remain contained in the original window represented by subsetTau.
     */
    private static boolean conditionHoldsAuxiliary(
            Interval sigmaDiff,
            Interval deltaUnion,
            Interval intersectTau,
            Interval subsetTau
    ) {
        Interval generatedTau =
                intIntersect(
                        intAdd(sigmaDiff, deltaUnion),
                        intersectTau
                );

        return isIntSubset(generatedTau, subsetTau);
    }

    /**
     * Identifies the direction in which the coalescability condition is checked.
     */
    private enum CoalescabilityCondition {
        ONE,
        TWO
    }

    /**
     * Prints this window in a compact human-readable form.
     */
    public void print() {
        String[] transformedVals = transformInfinities(
                new int[]{
                        sigma.lBound(),
                        sigma.uBound(),
                        tau.lBound(),
                        tau.uBound()
                }
        );

        System.out.println(
                "(s = [" + transformedVals[0] + ", " + transformedVals[1] + "]"
                        + "; d = [" + delta.lBound() + ", " + delta.uBound() + "]"
                        + "; t = [" + transformedVals[2] + ", " + transformedVals[3] + "])"
        );
    }

    /**
     * Converts integer sentinel values representing infinity into readable "-inf" and "+inf" strings.
     */
    private String[] transformInfinities(int[] vals) {
        String[] transformedVals = new String[vals.length];

        for (int i = 0; i < vals.length; i++) {
            if (vals[i] == NEG_INF) {
                transformedVals[i] = "-inf";
            } else if (vals[i] == POS_INF) {
                transformedVals[i] = "+inf";
            } else {
                transformedVals[i] = Integer.toString(vals[i]);
            }
        }

        return transformedVals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Window w = (Window) o;

        return sigma.equals(w.sigma)
                && delta.equals(w.delta)
                && tau.equals(w.tau);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sigma, delta, tau);
    }

    /**
     * Creates a copy of this window.
     */
    public Window deepCopy() {
        Window copy = new Window();

        copy.sigma = sigma;
        copy.delta = delta;
        copy.tau = tau;

        return copy;
    }
}