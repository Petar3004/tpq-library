package Operators;

import Data.ResultMap;

public abstract class AbstractOperator implements Operator {

    protected ResultMap r;

    @Override
    public ResultMap getResult() {
        return r;
    }
}
