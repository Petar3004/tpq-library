package Operators.Unary;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Data.Window;
import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Interval;
import Utils.Metrics;
import Utils.Utils.NodePair;

import java.util.List;
import java.util.stream.Collectors;

public class Converse extends AbstractOperator {

    // Child operator whose relation is inverted.
    private final Operator op;

    public Converse(Operator op) {
        this.op = op;
    }

    /**
     * Evaluates the child operator and computes the converse of its relation.
     *
     * Each node pair (n1, n2) is replaced by (n2, n1), and every associated temporal window is inverted accordingly.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        // Evaluate the child subtree first.
        op.eval(strategy);

        ResultMap childRelation = op.getResult();

        // Track the size of the relation before computing the converse.
        metrics.updateMaxCount(childRelation.windowCount());

        /*
         * Reverse every node pair and invert all temporal windows associated with it.
         */
        childRelation.results = childRelation.results
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> new NodePair(
                                entry.getKey().n2(),
                                entry.getKey().n1()
                        ),
                        entry -> invertWindows(entry.getValue())
                ));

        // Reuse the transformed child relation as this operator's result.
        r = childRelation;
    }

    /**
     * Inverts all temporal windows in a relation.
     *
     * For a window (sigma, delta, tau), the converse is:
     *
     *     sigma' = tau
     *     delta' = -delta
     *     tau'   = sigma
     *
     * where negating an interval [a, b] gives [-b, -a].
     */
    private List<Window> invertWindows(List<Window> windows) {
        return windows.stream().map(w -> new Window(
                new Interval(
                        w.tau.lBound(),
                        w.tau.uBound()
                ),
                new Interval(
                        -w.delta.uBound(),
                        -w.delta.lBound()
                ),
                new Interval(
                        w.sigma.lBound(),
                        w.sigma.uBound()
                )
        ))
        .collect(Collectors.toList());
    }
}