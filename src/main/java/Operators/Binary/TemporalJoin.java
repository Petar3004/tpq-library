package Operators.Binary;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Data.Window;
import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Interval;
import Utils.Metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Utils.Interval.*;
import static Utils.Utils.*;

public class TemporalJoin extends AbstractOperator {

    // Left and right child operators of the join.
    public Operator op1;
    public Operator op2;

    public TemporalJoin(Operator op1, Operator op2) {
        this.op1 = op1;
        this.op2 = op2;
    }

    /**
     * Evaluates both child operators and joins their temporal relations.
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

        // Track the largest intermediate relation encountered.
        metrics.updateMaxCount(leftChildRelation.windowCount());
        metrics.updateMaxCount(rightChildRelation.windowCount());

        /*
         * PREJOIN_COAL coalesces both input relations before performing the temporal join.
         */
        if (strategy == QueryStrategy.PREJOIN_COAL) {
            long startCoalTime = metrics.getCurrentTime();

            leftChildRelation.coalesce();
            rightChildRelation.coalesce();

            metrics.addTime(
                    TimeMetric.COALESCE,
                    metrics.getCurrentTime() - startCoalTime
            );
        }

        /*
         * PREJOIN_NORM fully normalizes both input relations before joining.
         */
        if (strategy == QueryStrategy.PREJOIN_NORM) {
            long startNormTime = metrics.getCurrentTime();

            int normalizedWindows = leftChildRelation.normalize(IntervalType.ALL);
            normalizedWindows += rightChildRelation.normalize(IntervalType.ALL);

            metrics.addNormalizedCount(normalizedWindows);

            metrics.addTime(
                    TimeMetric.NORMALIZE,
                    metrics.getCurrentTime() - startNormTime
            );
        }

        // Perform the actual temporal hash join.
        long startJoinTime = metrics.getCurrentTime();

        r = hashJoin(
                leftChildRelation,
                rightChildRelation
        );

        metrics.addTime(
                TimeMetric.JOIN,
                metrics.getCurrentTime() - startJoinTime
        );

        metrics.updateMaxCount(r.windowCount());

        // Joining invalidates the normalized state of the result (only in the delta).
        r.normalized = false;

        /*
         * FULL_NORM and MAXIMAL normalize the delta component immediately after the join.
         */
        if (strategy == QueryStrategy.FULL_NORM || strategy == QueryStrategy.MAXIMAL) {
            long startNormTime = metrics.getCurrentTime();

            int normalizedWindows = r.normalize(IntervalType.DELTA);

            metrics.addNormalizedCount(normalizedWindows);

            metrics.addTime(
                    TimeMetric.NORMALIZE,
                    metrics.getCurrentTime() - startNormTime
            );
        }

        /*
         * MAXIMAL additionally coalesces the join result.
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

    /**
     * Performs a hash join between two temporal relations.
     *
     * A pair (n1, n2) from the left relation joins with a pair (n2, n3) from the right relation, producing (n1, n3).
     * Their temporal windows are composed whenever their intermediate-time constraints are compatible.
     */
    private ResultMap hashJoin(ResultMap leftChild, ResultMap rightChild) {
        /*
         * Build a hash table on the first node of the right relation.
         *
         * For every right-hand pair (n2, n3), the table maps n2 to n3 together with its associated windows.
         */
        Map<String, List<NodeListPair>> hashTable = new HashMap<>();

        for (Map.Entry<NodePair, List<Window>> entry: rightChild.results.entrySet()) {
            hashTable.computeIfAbsent(
                    entry.getKey().n1(),
                    k -> new ArrayList<>()
            ).add(new NodeListPair(entry.getKey().n2(), entry.getValue()));
        }

        ResultMap join = new ResultMap();

        /*
         * Probe the hash table with the second node of each left-hand pair.
         */
        for (Map.Entry<NodePair, List<Window>> entry: leftChild.results.entrySet()) {
            List<NodeListPair> matchingRightEntries = hashTable.get(entry.getKey().n2());

            if (matchingRightEntries == null) {
                continue;
            }

            for (NodeListPair rightEntry : matchingRightEntries) {
                NodePair joinedNodes =
                        new NodePair(entry.getKey().n1(), rightEntry.n());

                List<Window> composedWindows = composeWindowLists(entry.getValue(), rightEntry.l());

                if (!composedWindows.isEmpty()) {
                    join.results
                            .computeIfAbsent(
                                    joinedNodes,
                                    k -> new ArrayList<>()
                            )
                            .addAll(composedWindows);
                }
            }
        }

        return join;
    }

    /**
     * Computes all valid pairwise compositions between two lists of
     * temporal windows.
     */
    private List<Window> composeWindowLists(List<Window> l1, List<Window> l2) {
        List<Window> composed = new ArrayList<>();

        for (Window w1 : l1) {
            for (Window w2 : l2) {
                Window comp = composeWindows(w1, w2);

                if (comp != null) {
                    composed.add(comp);
                }
            }
        }

        return composed;
    }

    /**
     * Composes two temporal windows.
     *
     * The composition is possible only if the end-time interval of the first
     * window overlaps the start-time interval of the second window.
     *
     * The resulting window represents the temporal constraints from the
     * start of w1 to the end of w2.
     *
     * @return the composed window, or null if no valid composition exists
     */
    private Window composeWindows(Window w1, Window w2) {
        // The intermediate time must satisfy both tau_1 and sigma_2.
        if (!intersectionExists(w1.tau, w2.sigma)) {
            return null;
        }

        Interval intermediate = intIntersect(w1.tau, w2.sigma);

        /*
         * Restrict the start interval of w1 to values that can reach a valid intermediate time using w1.delta.
         */
        Interval newSigma = intIntersect(w1.sigma, intSub(intermediate, w1.delta));

        // Durations / temporal differences compose additively.
        Interval newDelta = intAdd(w1.delta, w2.delta);

        /*
         * Restrict the final time to values reachable from the intermediate time using w2.delta and allowed by w2.tau.
         */
        Interval newTau = intIntersect(intAdd(intermediate, w2.delta), w2.tau);

        if (newSigma == null || newDelta == null || newTau == null) {
            return null;
        }

        return new Window(newSigma, newDelta, newTau);
    }
}