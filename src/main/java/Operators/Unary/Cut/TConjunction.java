package Operators.Unary.Cut;

import Data.Window;

import java.util.List;
import java.util.Set;

public class TConjunction implements TCondition {

    // Atomic temporal conditions that must all hold.
    private final Set<TAtom> tAtoms;

    public TConjunction(Set<TAtom> tAtoms) {
        this.tAtoms = tAtoms;
    }

    /**
     * Applies all temporal atoms sequentially.
     *
     * Each atom restricts the result produced by the previous atom, thereby implementing the conjunction of all conditions.
     *
     * @param windows windows to restrict
     * @return windows satisfying every atomic condition
     */
    @Override
    public List<Window> cut(List<Window> windows) {
        List<Window> result = windows;

        for (TAtom tAtom : tAtoms) {
            result = tAtom.cut(result);

            // No window can satisfy the conjunction once the result is empty.
            if (result.isEmpty()) {
                break;
            }
        }

        return result;
    }
}