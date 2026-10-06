package Operators.Unary.Select;

import Utils.Utils.NodePair;

public class Atom implements Condition {

    /**
     * Identifies which node of a node pair is tested.
     */
    public enum Node {
        N1,
        N2
    }

    // Node position to compare.
    private final Node n;

    // Value that the selected node must equal.
    private final String equalTo;

    public Atom(Node n, String equalTo) {
        this.n = n;
        this.equalTo = equalTo;
    }

    /**
     * Tests whether the selected node of the pair matches the required value.
     *
     * @param nodes node pair to test
     * @return true if the selected node equals the comparison value
     */
    @Override
    public boolean select(NodePair nodes) {
        return getNode(nodes).equals(equalTo);
    }

    /**
     * Returns the node selected by this atomic condition.
     */
    private String getNode(NodePair nodes) {
        return switch (n) {
            case N1 -> nodes.n1();
            case N2 -> nodes.n2();
        };
    }
}