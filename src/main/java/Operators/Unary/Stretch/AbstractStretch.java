package Operators.Unary.Stretch;

import Operators.AbstractOperator;
import Operators.Operator;
import Utils.Interval;
import Utils.Metrics;

public abstract class AbstractStretch extends AbstractOperator {

    public Operator op;
    public Interval stretch;

    public AbstractStretch(Operator op, Interval stretch) {
        this.op = op;
        this.stretch = stretch;
    }
}
