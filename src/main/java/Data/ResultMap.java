package Data;

import Utils.Interval;
import Utils.Utils.*;

import java.util.*;

public class ResultMap {

    // Indicates whether the stored windows have already been normalized.
    public boolean normalized = false;

    // Maps each node pair to the list of temporal windows associated with it.
    public Map<NodePair, List<Window>> results = new HashMap<>();

    /**
     * Normalizes all windows in the result map with respect to the given interval type.
     *
     * Windows that become invalid during normalization are removed.
     *
     * @param intType interval component(s) to normalize
     * @return the number of windows that were modified by normalization
     */
    public int normalize(IntervalType intType) {
        int normalizedWindows = 0;

        for (Map.Entry<NodePair, List<Window>> entry : results.entrySet()) {
            List<Window> windows = entry.getValue();
            ListIterator<Window> iterator = windows.listIterator();

            while (iterator.hasNext()) {
                Window w = iterator.next();

                // Normalize a copy so the original window is only replaced when normalization succeeds.
                Window normalizedWindow = w.deepCopy().normalize(intType);

                if (normalizedWindow == null) {
                    // Remove windows whose constraints are inconsistent.
                    iterator.remove();
                } else if (!normalizedWindow.equals(w)) {
                    iterator.set(normalizedWindow);
                    normalizedWindows++;
                }
            }
        }

        // Remove node pairs for which no valid windows remain.
        results.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        normalized = true;

        return normalizedWindows;
    }

    /**
     * Coalesces compatible windows independently for each node pair.
     */
    public void coalesce() {
        for (List<Window> windows : results.values()) {
            Window.coalesceList(windows);
        }
    }

    /**
     * Returns the number of distinct node pairs in the result.
     */
    public int pairCount() {
        return results.size();
    }

    /**
     * Returns the total number of windows across all node pairs.
     */
    public int windowCount() {
        return results.values()
                .stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Prints all node pairs and their associated windows.
     */
    public void print() {
        for (Map.Entry<NodePair, List<Window>> entry : results.entrySet()) {
            entry.getKey().print();

            int i = 0;
            for (Window w : entry.getValue()) {
                System.out.print("W" + i + ": ");
                w.print();
                i++;
            }
        }
    }

    /**
     * Creates an independent copy of this result map.
     *
     * Each Window and each of its Interval objects is recreated so that modifying the copy does not affect the original result.
     */
    public ResultMap deepCopy() {
        ResultMap copy = new ResultMap();

        for (Map.Entry<NodePair, List<Window>> entry : results.entrySet()) {
            List<Window> copiedList = new ArrayList<>();

            for (Window w : entry.getValue()) {
                copiedList.add(
                        new Window(
                                new Interval(
                                        w.sigma.lBound(),
                                        w.sigma.uBound()
                                ),
                                new Interval(
                                        w.delta.lBound(),
                                        w.delta.uBound()
                                ),
                                new Interval(
                                        w.tau.lBound(),
                                        w.tau.uBound()
                                )
                        )
                );
            }

            copy.results.put(entry.getKey(), copiedList);
        }

        return copy;
    }

    /**
     * Compares result maps independently of the ordering of windows within each node pair.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResultMap other = (ResultMap) o;

        if (results.size() != other.results.size()) {
            return false;
        }

        for (Map.Entry<NodePair, List<Window>> entry : results.entrySet()) {
            List<Window> otherList = other.results.get(entry.getKey());

            if (otherList == null ||
                    entry.getValue().size() != otherList.size()) {
                return false;
            }

            /*
             * Sort shallow copies so that equality does not depend on the order in which equivalent windows were inserted.
             */
            List<Window> thisCopy = new ArrayList<>(entry.getValue());
            List<Window> otherCopy = new ArrayList<>(otherList);

            thisCopy.sort(
                    Comparator
                            .comparing((Window w) -> w.sigma.lBound())
                            .thenComparing(w -> w.tau.lBound())
            );

            otherCopy.sort(
                    Comparator
                            .comparing((Window w) -> w.sigma.lBound())
                            .thenComparing(w -> w.tau.lBound())
            );

            if (!thisCopy.equals(otherCopy)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;

        for (Map.Entry<NodePair, List<Window>> entry : results.entrySet()) {
            List<Window> copy = new ArrayList<>(entry.getValue());

            // Use the same canonical ordering as equals().
            copy.sort(
                    Comparator
                            .comparing((Window w) -> w.sigma.lBound())
                            .thenComparing(w -> w.tau.lBound())
            );

            hash += Objects.hashCode(entry.getKey()) ^ copy.hashCode();
        }

        return hash;
    }
}
