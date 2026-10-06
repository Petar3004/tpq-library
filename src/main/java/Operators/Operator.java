package Operators;

import Data.Query.QueryStrategy;
import Data.ResultMap;
import Utils.Metrics;
import Utils.NoMetrics;

public interface Operator {

    default void eval(QueryStrategy strategy) {
        eval(strategy, NoMetrics.INSTANCE);
    }

    void eval(QueryStrategy strategy, Metrics metrics);

    ResultMap getResult();
}
