package Operators.Unary;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Data.Window;
import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Interval;
import Utils.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static Data.Query.QueryStrategy.MAXIMAL;
import static Utils.Utils.*;

public class Exists extends AbstractOperator {

    // Child operator whose second node is existentially projected away.
    private final Operator op;

    public Exists(Operator op) {
        this.op = op;
    }

    /**
     * Evaluates the child operator and performs existential projection.
     *
     * Each pair (n1, n2) is projected to the unary representation (n1, n1).
     * The temporal information is projected onto sigma, producing point windows of the form:
     *
     *     (sigma, [0, 0], sigma)
     *
     * Before projection, sigma is normalized if necessary.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        // Evaluate the child subtree first.
        op.eval(strategy);

        ResultMap childRelation = op.getResult();

        // Track the size of the relation before projection.
        metrics.updateMaxCount(childRelation.windowCount());

        /*
         * Projection depends only on sigma. If the child relation is not normalized,
         * normalize sigma before discarding the other components.
         */
        if (!childRelation.normalized) {
            long startNormTime = metrics.getCurrentTime();

            int normalizedWindows = childRelation.normalize(IntervalType.SIGMA);

            metrics.addTime(
                    TimeMetric.NORMALIZE,
                    metrics.getCurrentTime() - startNormTime
            );

            metrics.addNormalizedCount(normalizedWindows);
        }

        /*
         * Project each pair (n1, n2) to (n1, n1).
         *
         * Multiple original pairs may share the same n1, so their projected window lists are merged.
         */
        childRelation.results = childRelation.results
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> new NodePair(
                                entry.getKey().n1(),
                                entry.getKey().n1()
                        ),
                        entry -> projectWindows(
                                entry.getValue(),
                                strategy,
                                metrics
                        ),
                        (existingList, newList) -> {
                            existingList.addAll(newList);
                            return existingList;
                        }
                ));

        r = childRelation;
    }

    /**
     * Projects a list of temporal windows onto their sigma component.
     *
     * Each sigma interval becomes a unary window:
     *
     *     sigma' = sigma
     *     delta' = [0, 0]
     *     tau'   = sigma
     *
     * Under the MAXIMAL strategy, projected sigma intervals are coalesced
     * before being converted back into windows.
     */
    private List<Window> projectWindows(List<Window> windows, QueryStrategy strategy, Metrics metrics) {
        List<Interval> sigmas = new ArrayList<>();

        for (Window w : windows) {
            sigmas.add(
                    new Interval(
                            w.sigma.lBound(),
                            w.sigma.uBound()
                    )
            );
        }

        /*
         * MAXIMAL coalesces the projected intervals before rebuilding
         * the unary windows.
         */
        if (strategy == MAXIMAL) {
            long startCoalTime = metrics.getCurrentTime();

            List<Interval> mergedSigmas = coalesce(sigmas);

            metrics.addTime(
                    TimeMetric.COALESCE,
                    metrics.getCurrentTime() - startCoalTime
            );

            return mapIntervalsToWindows(mergedSigmas);
        }

        // Convert each projected sigma interval into a unary window.
        return mapIntervalsToWindows(sigmas);
    }
}