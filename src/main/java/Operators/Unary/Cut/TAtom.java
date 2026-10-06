package Operators.Unary.Cut;

import Data.Window;
import Utils.Interval;

import java.util.ArrayList;
import java.util.List;

import static Utils.Interval.intersectionExists;
import static Utils.Utils.*;

public class TAtom implements TCondition {

    /**
     * Identifies which endpoint of a temporal pair is constrained.
     */
    public enum TimePoint {
        T1,
        T2
    }

    /**
     * Comparison operator used in a temporal value condition.
     *
     * EQUAL_TO              represents =
     * LESS_THAN_OR_EQUAL    represents <=
     * GREATER_THAN_OR_EQUAL represents >=
     */
    public enum CompOperator {
        EQUAL_TO,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN_OR_EQUAL
    }

    // Comparison operator used for a temporal value condition.
    public CompOperator compOp;

    // Time point to which the comparison is applied.
    public TimePoint tp;

    // Comparison value used in a temporal value condition.
    public int compVal;

    // Window used directly for a window-intersection condition.
    public Window compW;

    /**
     * Creates an atomic temporal condition comparing one time point against a value.
     *
     * @param tp      time point to constrain
     * @param compOp  comparison operator
     * @param compVal comparison value
     */
    public TAtom(TimePoint tp, CompOperator compOp, int compVal) {
        this.tp = tp;
        this.compOp = compOp;
        this.compVal = compVal;
    }

    /**
     * Creates an atomic temporal condition represented directly by a window.
     *
     * Applying the condition intersects each input window with compW.
     *
     * @param compW comparison window
     */
    public TAtom(Window compW) {
        this.compW = compW;
    }

    /**
     * Applies this atomic temporal condition to a list of windows.
     *
     * A TAtom represents either:
     * - a comparison between T1 or T2 and a constant, or
     * - an intersection with a comparison window.
     *
     * @param l windows to restrict
     * @return newly created windows satisfying the condition
     */
    @Override
    public List<Window> cut(List<Window> l) {
        if (tp != null) {
            return cutWindows(l);
        }

        if (compW != null) {
            return intersectWindows(l, compW);
        }

        return new ArrayList<>();
    }

    /**
     * Constructs the comparison window corresponding to the temporal predicate and intersects the input windows with it.
     */
    private List<Window> cutWindows(List<Window> l) {
        Window comparisonWindow = switch (compOp) {
            case EQUAL_TO -> equalityWindow();
            case LESS_THAN_OR_EQUAL -> lessThanOrEqualWindow();
            case GREATER_THAN_OR_EQUAL -> greaterThanOrEqualWindow();
        };

        return intersectWindows(l, comparisonWindow);
    }

    /**
     * Constructs a window representing T1 = compVal or T2 = compVal.
     */
    private Window equalityWindow() {
        return switch (tp) {
            case T1 -> new Window(
                    new Interval(compVal, compVal),
                    FULL_INT,
                    FULL_INT
            );

            case T2 -> new Window(
                    FULL_INT,
                    FULL_INT,
                    new Interval(compVal, compVal)
            );
        };
    }

    /**
     * Constructs a window representing T1 <= compVal or T2 <= compVal.
     */
    private Window lessThanOrEqualWindow() {
        return switch (tp) {
            case T1 -> new Window(
                    new Interval(NEG_INF, compVal),
                    FULL_INT,
                    FULL_INT
            );

            case T2 -> new Window(
                    FULL_INT,
                    FULL_INT,
                    new Interval(NEG_INF, compVal)
            );
        };
    }

    /**
     * Constructs a window representing T1 >= compVal or T2 >= compVal.
     */
    private Window greaterThanOrEqualWindow() {
        return switch (tp) {
            case T1 -> new Window(
                    new Interval(compVal, POS_INF),
                    FULL_INT,
                    FULL_INT
            );

            case T2 -> new Window(
                    FULL_INT,
                    FULL_INT,
                    new Interval(compVal, POS_INF)
            );
        };
    }

    /**
     * Intersects every input window with the given comparison window.
     */
    private List<Window> intersectWindows(List<Window> windows, Window comparisonWindow) {
        List<Window> cut = new ArrayList<>();

        for (Window w : windows) {
            /*
             * A valid window intersection requires overlap in sigma, delta, and tau.
             */
            if (!intersectionExists(w.sigma, comparisonWindow.sigma)
                    || !intersectionExists(w.delta, comparisonWindow.delta)
                    || !intersectionExists(w.tau, comparisonWindow.tau)) {
                continue;
            }

            Interval sigmaIntersection = new Interval(
                    Math.max(
                            w.sigma.lBound(),
                            comparisonWindow.sigma.lBound()
                    ),
                    Math.min(
                            w.sigma.uBound(),
                            comparisonWindow.sigma.uBound()
                    )
            );

            Interval deltaIntersection = new Interval(
                    Math.max(
                            w.delta.lBound(),
                            comparisonWindow.delta.lBound()
                    ),
                    Math.min(
                            w.delta.uBound(),
                            comparisonWindow.delta.uBound()
                    )
            );

            Interval tauIntersection = new Interval(
                    Math.max(
                            w.tau.lBound(),
                            comparisonWindow.tau.lBound()
                    ),
                    Math.min(
                            w.tau.uBound(),
                            comparisonWindow.tau.uBound()
                    )
            );

            cut.add(
                    new Window(
                            sigmaIntersection,
                            deltaIntersection,
                            tauIntersection
                    )
            );
        }

        return cut;
    }
}