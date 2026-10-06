package Operators.Binary;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Data.Window;
import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Metrics;
import Utils.Utils;
import Utils.Utils.NodePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Union extends AbstractOperator {

    // Left and right child operators of the union.
    public Operator op1;
    public Operator op2;

    public Union(Operator op1, Operator op2) {
        this.op1 = op1;
        this.op2 = op2;
    }

    /**
     * Evaluates both child operators and computes the union of their results.
     *
     * Windows belonging to the same node pair are appended to the same list.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        // Evaluate both child subtrees first.
        op1.eval(strategy);
        op2.eval(strategy);

        ResultMap leftChildRelation = op1.getResult();
        ResultMap rightChildRelation = op2.getResult();

        /*
         * The union is normalized only if both input relations are normalized.
         * Since the left relation is reused as the result container, its flag is updated when the right relation is not normalized.
         */
        if (!rightChildRelation.normalized) {
            leftChildRelation.normalized = false;
        }

        // Track the sizes of the input intermediate relations.
        metrics.updateMaxCount(leftChildRelation.windowCount());
        metrics.updateMaxCount(rightChildRelation.windowCount());

        /*
         * Merge the right relation into the left one.
         *
         * If a node pair already exists, append its windows. Otherwise, create a new list for that pair.
         */
        for (Map.Entry<NodePair, List<Window>> entry: rightChildRelation.results.entrySet()) {
            leftChildRelation.results
                    .computeIfAbsent(
                            entry.getKey(),
                            k -> new ArrayList<>()
                    )
                    .addAll(entry.getValue());
        }

        // Reuse the merged left relation as this operator's result.
        r = leftChildRelation;

        metrics.updateMaxCount(r.windowCount());

        /*
         * MAXIMAL additionally coalesces compatible windows in the result.
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