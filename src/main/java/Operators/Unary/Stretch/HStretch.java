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
import static Utils.Interval.intSub;

public class HStretch extends AbstractStretch {

    public HStretch(Operator op, Interval stretch) {
        super(op, stretch);
    }

    /**
     * Evaluates the child operator and applies a horizontal stretch to every temporal window in the result.
     *
     * The transformation modifies sigma and delta while leaving tau unchanged.
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
         * Apply the horizontal stretch to every window.
         *
         * Expanding sigma backwards by the stretch interval requires delta
         * to be expanded forward so that tau remains unchanged.
         */
        for (List<Window> windows : childRelation.results.values()) {
            for (Window w : windows) {
                w.sigma = intSub(w.sigma, stretch);
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