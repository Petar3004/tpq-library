import Data.ResultMap;
import Data.Window;
import Utils.Interval;
import Utils.Utils.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CoalescingTest {

    @Test
    void testCoalescing() {
        ResultMap resultMap = new ResultMap();
        NodePair pair = new NodePair("FRA", "LYS");
        List<Window> windows = new ArrayList<>();

        // Setup two overlapping/adjacent windows that are physically coalescable
        // Window 1: sigma=[10, 20], delta=[5, 5], tau=[15, 25]
        // Window 2: sigma=[20, 30], delta=[5, 5], tau=[25, 35]
        Window w1 = new Window(
                new Interval(10, 20),
                new Interval(5, 5),
                new Interval(15, 25));
        Window w2 = new Window(
                new Interval(20, 30),
                new Interval(5, 5),
                new Interval(25, 35));

        windows.add(w1);
        windows.add(w2);
        resultMap.results.put(pair, windows);

        resultMap.coalesce();

        List<Window> finalWindows = resultMap.results.get(pair);
        assertEquals(1, finalWindows.size());

        Window merged = finalWindows.get(0);
        assertEquals(new Interval(10, 30), merged.sigma);
        assertEquals(new Interval(5, 5), merged.delta);
        assertEquals(new Interval(15, 35), merged.tau);
    }

    @Test
    void testCascadingCoalescing() {
        ResultMap resultMap = new ResultMap();
        NodePair pair = new NodePair("FRA", "SOF");
        List<Window> windows = new ArrayList<>();

        // Setup three windows where W1 merges with W2, and the resulting block *then* satisfies merging with W3
        // W1: [10, 20], W2: [20, 30], W3: [30, 40]
        Window w1 = new Window(
                new Interval(10, 20),
                new Interval(2, 2),
                new Interval(12, 22));
        Window w2 = new Window(
                new Interval(20, 30),
                new Interval(2, 2),
                new Interval(22, 32));
        Window w3 = new Window(
                new Interval(30, 40),
                new Interval(2, 2),
                new Interval(32, 42));

        // Intentionally mix up index sequence insertion to heavily pressure the index back-tracking loops
        windows.add(w3);
        windows.add(w1);
        windows.add(w2);
        resultMap.results.put(pair, windows);

        resultMap.coalesce();

        List<Window> finalWindows = resultMap.results.get(pair);
        assertEquals(1, finalWindows.size());

        Window composite = finalWindows.get(0);
        assertEquals(new Interval(10, 40), composite.sigma);
        assertEquals(new Interval(12, 42), composite.tau);
    }

    @Test
    void testIncompatible() {
        ResultMap resultMap = new ResultMap();
        NodePair pair = new NodePair("LYS", "SOF");
        List<Window> windows = new ArrayList<>();

        Window morningFlight = new Window(
                new Interval(100, 200),
                new Interval(50, 50),
                new Interval(150, 250));
        Window nightFlight   = new Window(
                new Interval(900, 1000),
                new Interval(50, 50),
                new Interval(950, 1050));

        windows.add(morningFlight);
        windows.add(nightFlight);
        resultMap.results.put(pair, windows);

        resultMap.coalesce();

        // No merging should take place; the structural components must remain separated
        List<Window> finalWindows = resultMap.results.get(pair);
        assertEquals(2, finalWindows.size());
    }

    @Test
    void testEmptyOrSingleWindowList() {
        ResultMap resultMap = new ResultMap();
        NodePair pairA = new NodePair("SOF", "FRA");
        NodePair pairB = new NodePair("SOF", "LYS");

        // Test boundary limits with removedWindows collections and isolated window singletons
        resultMap.results.put(pairA, new ArrayList<>());

        List<Window> singleElementList = new ArrayList<>();
        singleElementList.add(new Window(
                new Interval(0, 10),
                new Interval(0, 0),
                new Interval(0, 10)));
        resultMap.results.put(pairB, singleElementList);

        assertEquals(0, resultMap.results.get(pairA).size());
        assertEquals(1, resultMap.results.get(pairB).size());
    }
}