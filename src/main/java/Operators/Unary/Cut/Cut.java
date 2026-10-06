package Operators.Unary.Cut;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Data.Window;
import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Metrics;
import Utils.Utils;
import Utils.Utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Cut extends AbstractOperator {

    // Child operator whose result is restricted by the temporal condition.
    Operator op;

    // Temporal condition used to cut the windows of the child relation.
    TCondition tCond;

    public Cut(Operator op, TCondition tCond) {
        this.op = op;
        this.tCond = tCond;
    }

    /**
     * Evaluates the child operator and applies the temporal cut condition to every node pair in its result.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        // Evaluate the child subtree first.
        op.eval(strategy);

        ResultMap childRelation = op.getResult();

        // Track the size of the input relation before applying the cut.
        metrics.updateMaxCount(childRelation.windowCount());

        /*
         * Apply the temporal condition independently to the windows associated with each node pair.
         */
        for (Map.Entry<NodePair, List<Window>> entry: childRelation.results.entrySet()) {
            List<Window> cutWindows = tCond.cut(entry.getValue());

            /*
             * A null result indicates that no window satisfies the condition. (can be later removed by normalization)
             */
            if (cutWindows == null) {
                childRelation.results.put(
                        entry.getKey(),
                        new ArrayList<>()
                );
            } else {
                childRelation.results.put(
                        entry.getKey(),
                        cutWindows
                );
            }
        }

        // Reuse the modified child relation as this operator's result.
        r = childRelation;

        // Cutting invalidates the normalized state of the relation.
        r.normalized = false;

        /*
         * FULL_NORM and MAXIMAL fully normalize the resulting windows.
         */
        if (strategy == QueryStrategy.FULL_NORM || strategy == QueryStrategy.MAXIMAL) {
            long startNormTime = metrics.getCurrentTime();

            int normalizedWindows = r.normalize(IntervalType.ALL);

            metrics.addNormalizedCount(normalizedWindows);

            metrics.addTime(
                    Utils.TimeMetric.NORMALIZE,
                    metrics.getCurrentTime() - startNormTime
            );
        }

        /*
         * MAXIMAL additionally coalesces compatible windows.
         */
        if (strategy == QueryStrategy.MAXIMAL) {
            long startCoalTime = metrics.getCurrentTime();

            r.coalesce();

            metrics.addTime(
                    TimeMetric.COALESCE,
                    metrics.getCurrentTime() - startCoalTime
            );
        }
    }
}