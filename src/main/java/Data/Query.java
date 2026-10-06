package Data;

import Operators.Operator;
import Operators.Unary.Exists;
import Utils.Interval;
import Utils.Utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static Data.Query.QueryStrategy.MAXIMAL;
import static Utils.Utils.coalesce;
import static Utils.Utils.mapIntervalsToWindows;

public class Query {

    /**
     * Defines the evaluation strategy used during evaluation.
     */
    public enum QueryStrategy {
        MINIMAL,
        PREJOIN_COAL,
        PREJOIN_NORM,
        FULL_NORM,
        MAXIMAL
    }

    /**
     * Evaluates a query starting from the given operator tree.
     *
     * After the operator tree has been evaluated, the resulting windows can optionally be coalesced and/or normalized.
     * The MAXIMAL strategy always performs both final coalescing and final normalization.
     *
     * @param root           root operator of the query plan
     * @param strategy       evaluation strategy used by the operators
     * @param normalizeFinal whether the final result should be normalized
     * @param coalesceFinal  whether the final result should be coalesced
     * @return the final query result
     */
    public static ResultMap eval(
            Operator root,
            QueryStrategy strategy,
            boolean normalizeFinal,
            boolean coalesceFinal
    ) {
        // Evaluate the complete operator tree according to the chosen strategy.
        root.eval(strategy);

        ResultMap result = root.getResult();

        /*
         * Perform final coalescing when explicitly requested or when using the MAXIMAL strategy.
         */
        if (coalesceFinal || strategy == MAXIMAL) {

            /*
             * EXISTS produces intervals. Only the sigma intervals are relevant in this case,
             * so they are coalesced separately and then converted back into windows.
             */
            if (root instanceof Exists) {
                for (Map.Entry<NodePair, List<Window>> e : result.results.entrySet()) {
                    List<Interval> sigmas = new ArrayList<>();

                    for (Window w : e.getValue()) {
                        sigmas.add(w.sigma);
                    }

                    coalesce(sigmas);

                    e.setValue(mapIntervalsToWindows(sigmas));
                }
            } else {
                // For non-unary results, coalesce complete temporal windows.
                result.coalesce();
            }
        }

        /*
         * Normalize the final result when explicitly requested or when using the MAXIMAL strategy.
         * Avoid repeating normalization if the result is already normalized.
         */
        if ((normalizeFinal || strategy == MAXIMAL) && !result.normalized) {
            result.normalize(IntervalType.ALL);
        }

        return result;
    }
}