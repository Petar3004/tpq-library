package Operators.Nullary;

import Data.Graph;
import Data.Query.QueryStrategy;
import Data.ResultMap;
import Operators.AbstractOperator;
import Utils.Metrics;
import Utils.Utils;
import Utils.Utils.IntervalType;

public class Read extends AbstractOperator {

    // Name of the relation to read from the graph.
    private String relName;

    // Graph containing the relation to be read.
    private Graph g;

    /**
     * Creates a read operator for a named relation stored in a graph.
     *
     * @param relName name of the relation to read
     * @param g       graph containing the relation
     */
    public Read(String relName, Graph g) {
        this.relName = relName;
        this.g = g;
    }

    /**
     * Creates a read operator directly from an existing result map.
     *
     * The supplied result is copied so that later evaluation does not modify the original object.
     *
     * @param r relation to use as the input
     */
    public Read(ResultMap r) {
        this.r = r.deepCopy();
    }

    /**
     * Reads the input relation and applies any preprocessing required by the selected query strategy.
     *
     * @param strategy query evaluation strategy
     * @param metrics  object used to collect runtime and intermediate-result metrics
     */
    @Override
    public void eval(QueryStrategy strategy, Metrics metrics) {
        /*
         * Always work on a copy of the input relation so that evaluation
         * does not modify the graph or the originally supplied ResultMap.
         */
        if (relName != null) {
            r = g.graph.get(relName).deepCopy();
        } else if (r != null) {
            r = r.deepCopy();
        }

        // Track the size of the relation immediately after reading it.
        metrics.updateMaxCount(r.windowCount());

        /*
         * FULL_NORM and MAXIMAL normalize all three interval components
         * before the relation is consumed by parent operators.
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
         * MAXIMAL additionally coalesces compatible windows immediately
         * after the relation has been read and normalized.
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