import Data.*;
import Operators.Binary.TemporalJoin;
import Operators.Nullary.Read;
import Utils.Interval;
import Utils.Utils.*;
import org.junit.jupiter.api.Test;
import Utils.Utils.IntervalType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizationTest {

    @Test
    void testWindowNormalize() {
        Window w1 = new Window(
                new Interval(0, 30),
                new Interval(2, 5),
                new Interval(10, 20)
        );

        Window n1 = w1.normalize(IntervalType.SIGMA);

        assertNotNull(n1);
        assertEquals(new Interval(5, 18), n1.sigma);
        assertEquals(new Interval(2, 5), n1.delta);
        assertEquals(new Interval(10, 20), n1.tau);

        Window w2 = new Window(
                new Interval(5, 8),
                new Interval(0, 20),
                new Interval(10, 20)
        );

        Window n2 = w2.normalize(IntervalType.DELTA);

        assertNotNull(n2);
        assertEquals(new Interval(2, 15), n2.delta);
        assertEquals(new Interval(5, 8), n2.sigma);
        assertEquals(new Interval(10, 20), n2.tau);

        Window w3 = new Window(
                new Interval(5, 10),
                new Interval(2, 4),
                new Interval(0, 100));

        Window n3 = w3.normalize(IntervalType.TAU);

        assertNotNull(n3);
        assertEquals(new Interval(7, 14), n3.tau);
        assertEquals(new Interval(5, 10), n3.sigma);
        assertEquals(new Interval(2, 4), n3.delta);

        Window w4 = new Window(
                new Interval(0, 10),
                new Interval(0, 10),
                new Interval(8, 12));

        Window n4 = w4.normalize(IntervalType.ALL);

        assertNotNull(n4);
        assertEquals(new Interval(0, 10), n4.sigma);
        assertEquals(new Interval(0, 10), n4.delta);
        assertEquals(new Interval(8, 12), n4.tau);
    }

    @Test
    void testWindowNormalizeEmpty() {
        Window w = new Window(
                new Interval(0, 5),
                new Interval(10, 15),
                new Interval(0, 5));

        Window normalized = w.normalize(IntervalType.ALL);

        assertNull(normalized);
    }

    @Test
    void testResultMapNormalize() {
        ResultMap resultMap = new ResultMap();
        NodePair pairA = new NodePair("FRA", "LYS");
        NodePair pairB = new NodePair("FRA", "SOF");

        // Pair A gets one valid window and one completely invalid window
        List<Window> listA = new ArrayList<>();
        listA.add(new Window(
                new Interval(0, 30),
                new Interval(2, 5),
                new Interval(10, 20))); // valid
        listA.add(new Window(
                new Interval(0, 1),
                new Interval(50, 60),
                new Interval(0, 1)));  // invalid

        // Pair B gets ONLY an invalid window (its key should be pruned entirely)
        List<Window> listB = new ArrayList<>();
        listB.add(new Window(
                new Interval(0, 1),
                new Interval(50, 60),
                new Interval(0, 1)));  // invalid

        resultMap.results.put(pairA, listA);
        resultMap.results.put(pairB, listB);

        resultMap.normalize(IntervalType.ALL);

        // 1. Pair B should be entirely pruned because its window list became removedWindows
        assertFalse(resultMap.results.containsKey(pairB));

        // 2. Pair A should still exist
        assertTrue(resultMap.results.containsKey(pairA));

        // 3. Pair A's window list size should drop from 2 down to 1
        List<Window> preservedWindows = resultMap.results.get(pairA);
        assertEquals(1, preservedWindows.size());

        // 4. Verify the remaining window was correctly normalized
        Window validWindow = preservedWindows.get(0);
        assertEquals(new Interval(5, 18), validWindow.sigma);
    }

    @Test
    void testNormalizeDuringAndAfter() {
        FlightGraph g = new FlightGraph();
        g.fetchTsv("src/test/resources/test-flights3.tsv", 6, 100);

        ResultMap r1 = Query.eval(
                new TemporalJoin(
                        new Read("travels", g),
                        new Read("travels", g)
                ),
                Query.QueryStrategy.FULL_NORM,
                false,
                false
        );

        ResultMap r2 = Query.eval(
                new TemporalJoin(
                        new Read("travels", g),
                        new Read("travels", g)
                ),
                Query.QueryStrategy.MINIMAL,
                true,
                false
        );


        assertEquals(r1, r2);
    }
}