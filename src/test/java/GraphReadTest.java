import Data.ResultMap;
import Data.Window;
import Utils.Utils.NodePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphReadTest {

    FlightGraph g = new FlightGraph();
    String flights = "src/test/resources/test-flights.tsv";

    @Test
    void testSimpleFlightCreatesEdge() {
        g.fetchTsv(flights, 1, 100);

        ResultMap graph = g.graph.get("travels");

        Assertions.assertNotNull(graph);

        NodePair pair = new NodePair("FRA", "LYS");

        Assertions.assertTrue(graph.results.containsKey(pair));
        Assertions.assertFalse(graph.results.get(pair).isEmpty());
    }


    @Test
    void testFlightIsDuplicatedAfter24Hours() {
        g.fetchTsv(flights, 2, 100);

        ResultMap graph = g.graph.get("travels");

        List<Window> windows = graph.results.get(new NodePair("FRA", "LYS"));

        assertEquals(2, windows.size());

        Window first = windows.get(0);
        Window second = windows.get(1);

        assertEquals(first.sigma.lBound() + 1440, second.sigma.lBound());

        assertEquals(first.tau.lBound() + 1440, second.tau.lBound());
    }


    @Test
    void testMidnightFlightIsNotDuplicated() {
        g.fetchTsv(flights, 2, 100);

        ResultMap graph = g.graph.get("travels");

        List<Window> windows = graph.results.get(new NodePair("LHR", "ICN"));

        assertEquals(1, windows.size());

        Window window = windows.get(0);

        // arrival should be after 1440
        Assertions.assertTrue(window.tau.uBound() > 1440);
    }


    @Test
    void testSameFlightUpdatesExistingWindow() {
        g.fetchTsv(flights, 2, 100);

        ResultMap graph = g.graph.get("travels");

        List<Window> windows = graph.results.get(new NodePair("FRA", "LYS"));

        // should still only contain the original + shifted deepCopy
        assertEquals(2, windows.size());

        Window window = windows.get(0);

        // check that the interval was expanded
        Assertions.assertTrue(window.sigma.lBound() <= 12 * 60 + 1);
        Assertions.assertTrue(window.tau.uBound() >= 12 * 60 + 55);
    }

    @Test
    void testMoreRepeats() {
        g.fetchTsv(flights, 0, 100);

        assertEquals(g.windowCount(), 4);

        g.fetchTsv(flights, 2, 100);

        assertEquals(g.windowCount(), 7);

        g.fetchTsv(flights, 10, 100);

        assertEquals(g.windowCount(), 39);
    }

    @Test
    void testNodePercentage() {
       // TODO
    }
}