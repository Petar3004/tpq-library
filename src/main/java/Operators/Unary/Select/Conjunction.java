package Operators.Unary.Select;

import Utils.Utils.NodePair;

import java.util.Set;

public class Conjunction implements Condition {

    // Atomic node conditions that must all be satisfied.
    private final Set<Atom> atoms;

    public Conjunction(Set<Atom> atoms) {
        this.atoms = atoms;
    }

    /**
     * Tests whether a node pair satisfies every atomic condition in this conjunction.
     *
     * @param nodes node pair to test
     * @return true if all atomic conditions hold
     */
    @Override
    public boolean select(NodePair nodes) {
        for (Atom atom : atoms) {
            if (!atom.select(nodes)) {
                return false;
            }
        }

        return true;
    }
}