package Operators.Unary.Stretch;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Data.Window;
import Operators.Operator;
import Utils.Interval;
import Utils.Metrics;
import Utils.Utils;

import java.util.List;

import static Utils.Interval.intAdd;

public class VStretch extends AbstractStretch {

    public VStretch(Operator op, Interval stretch) {
        super(op, stretch);
    }

    /**
     * Evaluates the child operator and applies a vertical stretch to every temporal window in the result.
     *
     * The transformation modifies tau and delta while leaving sigma unchanged.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        // Evaluate the child subtree first.
        op.eval(strategy);

        ResultMap childRelation = op.getResult();

        // Track the size of the relation before applying the stretch.
        metrics.updateMaxCount(childRelation.windowCount());

        /*
         * Apply the vertical stretch to every window.
         *
         * Expanding tau by the stretch interval requires delta to be
         * expanded by the same amount so that sigma remains unchanged.
         */
        for (List<Window> windows : childRelation.results.values()) {
            for (Window w : windows) {
                w.tau = intAdd(w.tau, stretch);
                w.delta = intAdd(w.delta, stretch);
            }
        }

        // Reuse the modified child relation as this operator's result.
        r = childRelation;

        /*
         * MAXIMAL additionally coalesces windows that become compatible after stretching.
         */
        if (strategy == QueryStrategy.MAXIMAL) {
            long startCoalTime = metrics.getCurrentTime();

            r.coalesce();

            metrics.addTime(
                    Utils.TimeMetric.COALESCE,
                    metrics.getCurrentTime() - startCoalTime
            );
        }
    }
}