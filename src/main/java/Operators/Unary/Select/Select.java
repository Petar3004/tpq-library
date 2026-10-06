package Operators.Unary.Select;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Metrics;

public class Select extends AbstractOperator {

    // Child operator whose result is filtered.
    private final Operator op;

    // Predicate used to decide which node pairs are retained.
    private final Condition cond;

    public Select(Operator op, Condition cond) {
        this.op = op;
        this.cond = cond;
    }

    /**
     * Evaluates the child operator and removes all node pairs that do not satisfy the selection condition.
     *
     * The temporal windows associated with retained node pairs are left unchanged.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        // Evaluate the child subtree first.
        op.eval(strategy);

        ResultMap childRelation = op.getResult();

        // Track the size of the relation before applying the selection.
        metrics.updateMaxCount(childRelation.windowCount());

        /*
         * Remove every node pair that does not satisfy the selection predicate.
         * The associated windows are removed together with it.
         */
        childRelation.results
                .keySet()
                .removeIf(nodePair -> !cond.select(nodePair));

        // Reuse the filtered child relation as this operator's result.
        r = childRelation;
    }
}