import Data.*;
import Operators.Binary.TemporalJoin;
import Operators.Binary.Union;
import Operators.Nullary.Read;
import Operators.Operator;
import Operators.Unary.Converse;
import Operators.Unary.Cut.Cut;
import Operators.Unary.Cut.TAtom;
import Operators.Unary.Exists;
import Operators.Unary.Select.Atom;
import Operators.Unary.Select.Conjunction;
import Operators.Unary.Select.Select;
import Operators.Unary.Stretch.HStretch;
import Operators.Unary.Stretch.VStretch;
import Utils.Utils.*;
import Utils.Interval;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorTest {

    FlightGraph g = new FlightGraph();
    String testFlights = "src/test/resources/test-flights.tsv";

    @Test
    void testRead() {
        g.fetchTsv(testFlights, 1, 100);

        ResultMap r1 = Query.eval(
                new Read("travels", g),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertEquals(g.graph.get("travels"), r1);

        ResultMap r2 = Query.eval(
                new Read(r1),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertEquals(r2, r1);
    }

    @Test
    void testSelect() {
        g.fetchTsv(testFlights, 2, 100);

        ResultMap r1 = Query.eval(
                new Select(
                        new Read("travels", g),
                        new Atom(Atom.Node.N1, "FRA")
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        ResultMap fromFra = new ResultMap();
        fromFra.results.put(
                new NodePair("FRA", "LYS"),
                List.of(
                        new Window(
                                new Interval(718, 724),
                                new Interval(54,62),
                                new Interval(775, 781)
                        ),
                        new Window(
                                new Interval(718+1440, 724+1440),
                                new Interval(54,62),
                                new Interval(775+1440, 781+1440)
                        )
                )
        );
        fromFra.results.put(
                new NodePair("FRA", "SOF"),
                List.of(
                        new Window(
                                new Interval(942, 958),
                                new Interval(54, 58),
                                new Interval(997, 1014)
                        ),
                        new Window(
                                new Interval(942+1440, 958+1440),
                                new Interval(54, 58),
                                new Interval(997+1440, 1014+1440)
                        )
                )
        );

        assertEquals(r1, fromFra);

        ResultMap r2 = Query.eval(
                new Select(
                        new Read("travels", g),
                        new Conjunction(Set.of(
                                new Atom(Atom.Node.N1, "FRA"),
                                new Atom(Atom.Node.N2, "LYS")
                        ))
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        ResultMap fromFraToLys = new ResultMap();
        fromFraToLys.results.put(
                new NodePair("FRA", "LYS"),
                List.of(
                        new Window(
                                new Interval(718, 724),
                                new Interval(54,62),
                                new Interval(775, 781)
                        ),
                        new Window(
                                new Interval(718+1440, 724+1440),
                                new Interval(54,62),
                                new Interval(775+1440, 781+1440)
                        )
                )
        );

        assertEquals(r2, fromFraToLys);
    }

    @Test
    void testCut() {
        g.fetchTsv(testFlights, 1, 100);

        TAtom cond1 = new TAtom(TAtom.TimePoint.T1, TAtom.CompOperator.LESS_THAN_OR_EQUAL, 800);

        ResultMap r1 = Query.eval(
                new Cut(new Read("travels", g), cond1),
                Query.QueryStrategy.MINIMAL,
                true,
                false
        );

        for (List<Window> windows : r1.results.values()) {
            for (Window w : windows) {
                assertTrue(w.sigma.uBound() <= 800);
            }
        }

        TAtom cond2 = new TAtom(TAtom.TimePoint.T1, TAtom.CompOperator.GREATER_THAN_OR_EQUAL, 800);
        ResultMap r2 = Query.eval(
                new Cut(
                        new Read("travels", g), cond2),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertFalse(r2.results.isEmpty());
        for (List<Window> windows : r2.results.values()) {
            for (Window w : windows) {
                assertTrue(w.sigma == null || w.sigma.lBound() >= 800);
            }
        }

        TAtom cond3 = new TAtom(TAtom.TimePoint.T1, TAtom.CompOperator.EQUAL_TO, 720);
        ResultMap r3 = Query.eval(
                new Cut(
                        new Read("travels", g), cond3),
                Query.QueryStrategy.MINIMAL,
                false,
                false);

        assertFalse(r3.results.isEmpty());
        for (List<Window> windows : r3.results.values()) {
            for (Window w : windows) {
                assertTrue(w.sigma == null || w.sigma.lBound() == 720);
                assertTrue(w.sigma == null || w.sigma.uBound() == 720);
            }
        }

        TAtom cond4 = new TAtom(TAtom.TimePoint.T2, TAtom.CompOperator.LESS_THAN_OR_EQUAL, 1000);
        ResultMap r4 = Query.eval(
                new Cut(
                        new Read("travels", g), cond4),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertFalse(r4.results.isEmpty());
        for (List<Window> windows : r4.results.values()) {
            for (Window w : windows) {
                assertTrue(w.tau == null || w.tau.uBound() <= 1000);
            }
        }

        TAtom cond5 = new TAtom(TAtom.TimePoint.T2, TAtom.CompOperator.GREATER_THAN_OR_EQUAL, 1000);
        ResultMap r5 = Query.eval(
                new Cut(
                        new Read("travels", g), cond5),
                Query.QueryStrategy.MINIMAL,
                false,
                false);

        assertFalse(r5.results.isEmpty());
        for (List<Window> windows : r5.results.values()) {
            for (Window w : windows) {
                assertTrue(w.tau == null || w.tau.lBound() >= 1000);
            }
        }

        TAtom cond6 = new TAtom(TAtom.TimePoint.T2, TAtom.CompOperator.EQUAL_TO, 780);
        ResultMap r6 = Query.eval(
                new Cut(
                        new Read("travels", g), cond6),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertFalse(r6.results.isEmpty());
        for (List<Window> windows : r6.results.values()) {
            for (Window w : windows) {
                assertTrue(w.tau == null || w.tau.lBound() == 780);
                assertTrue(w.tau == null || w.tau.uBound() == 780);
            }
        }

        Window mask = new Window(
                new Interval(720, 750),  // restrictive departure bound
                new Interval(55, 60),    // restrictive duration bound
                new Interval(776, 790)   // restrictive arrival bound
        );
        TAtom cond7 = new TAtom(mask);

        ResultMap r7 = Query.eval(
                new Cut(
                        new Read("travels", g), cond7),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertFalse(r7.results.isEmpty());
        for (List<Window> windows : r7.results.values()) {
            for (Window w : windows) {
                // Ensure boundaries strictly fall within the intersecting window bounds
                assertTrue((w.sigma == null || w.delta == null || w.tau == null) ||
                                (w.sigma.lBound() >= 720 && w.sigma.uBound() <= 750) &&
                                (w.delta.lBound() >= 55  && w.delta.uBound() <= 60) &&
                                (w.tau.lBound() >= 776   && w.tau.uBound() <= 790));
            }
        }
    }

    @Test
    void testHStretch() {
        g.fetchTsv(testFlights, 1, 100);

        // Stretching horizontally by [10, 20]
        Interval stretch = new Interval(10, 20);
        ResultMap r = Query.eval(
                new HStretch(
                        new Select(new Read("travels", g), new Atom(Atom.Node.N1, "FRA")),
                        stretch
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        Window fraLysFlight = r.results.get(new NodePair("FRA", "LYS")).get(0);

        assertEquals(new Interval(698, 714), fraLysFlight.sigma);
        assertEquals(new Interval(64, 82), fraLysFlight.delta);
    }

    @Test
    void testVStretch() {
        g.fetchTsv(testFlights, 1, 100);

        // Stretching vertically by [5, 15]
        Interval stretch = new Interval(5, 15);
        ResultMap r = Query.eval(
                new VStretch(
                        new Select(new Read("travels", g), new Atom(Atom.Node.N1, "FRA")),
                        stretch
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        Window fraLysFlight = r.results.get(new NodePair("FRA", "LYS")).get(0);

        assertEquals(new Interval(780, 796), fraLysFlight.tau);
        assertEquals(new Interval(59, 77), fraLysFlight.delta);
    }

    @Test
    void testConverse() {
        g.fetchTsv(testFlights, 1, 100);

        ResultMap r = Query.eval(
                new Converse(
                        new Select(new Read("travels", g), new Atom(Atom.Node.N1, "FRA"))
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertTrue(r.results.containsKey(new NodePair("LYS", "FRA")));
        assertTrue(r.results.containsKey(new NodePair("SOF", "FRA")));

        Window invertedFlight = r.results.get(new NodePair("LYS", "FRA")).get(0);

        assertEquals(new Interval(775, 781), invertedFlight.sigma);
        assertEquals(new Interval(718, 724), invertedFlight.tau);
        assertEquals(new Interval(-62, -54), invertedFlight.delta);
    }

    @Test
    void testExists() {
        g.fetchTsv(testFlights, 1, 100);

        ResultMap r1 = Query.eval(
                new Exists(
                        new Select(new Read("travels", g), new Atom(Atom.Node.N1, "FRA"))
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                 false
        );

        assertTrue(r1.results.containsKey(new NodePair("FRA", "FRA")));
        assertFalse(r1.results.containsKey(new NodePair("FRA", "LYS")));

        Window existWindow = r1.results.get(new NodePair("FRA", "FRA")).get(0);
        assertEquals(new Interval(0, 0), existWindow.delta);
        assertEquals(existWindow.sigma, existWindow.tau);

        ResultMap r2 = Query.eval(
                new Exists(
                        new Read("travels", g)
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                true
        );

        ResultMap r3 = Query.eval(
                new Exists(
                        new Read("travels", g)
                ),
                Query.QueryStrategy.MAXIMAL,
                false,
                false
        );

        assertEquals(r2, r3);
    }

    @Test
    void testUnion() {
        g.fetchTsv(testFlights, 3, 100);

        Operator op1 = new Select(
                new Read("travels", g),
                new Conjunction(Set.of(new Atom(Atom.Node.N1, "FRA"), new Atom(Atom.Node.N2, "LYS")))
        );
        Operator op2 = new Select(
                new Read("travels", g),
                new Conjunction(Set.of(new Atom(Atom.Node.N1, "FRA"), new Atom(Atom.Node.N2, "SOF")))
        );

        ResultMap r = Query.eval(
                new Union(op1, op2),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertTrue(r.results.containsKey(new NodePair("FRA", "LYS")));
        assertTrue(r.results.containsKey(new NodePair("FRA", "SOF")));

        assertEquals(2, r.pairCount());
        assertEquals(6, r.windowCount());
    }

    @Test
    void testTemporalJoin() {
        g.fetchTsv(testFlights, 1, 100);

        Operator flights = new Read("travels", g);
        ResultMap r1 = Query.eval(
                new TemporalJoin(
                        flights,
                        flights
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                 false
        );

        assertEquals(r1.windowCount(), 0);

        FlightGraph g2 = new FlightGraph();
        g2.fetchTsv("src/test/resources/test-flights2.tsv", 1, 100);

        Operator flights2 = new Read("travels", g2);
        Query.eval(flights2, Query.QueryStrategy.MINIMAL, false, false);
        ResultMap nr = new ResultMap();
        nr.results.put(new NodePair("FRA", "FRA"), List.of(
                new Window(
                        new Interval(718, 724),
                        new Interval(176, 197),
                        new Interval(897, 913)
                )
        ));
        nr.results.put(new NodePair("FRA", "ICN"), List.of(
                new Window(
                        new Interval(942, 945),
                        new Interval(495, 562),
                        new Interval(1440, 1497)
                )
        ));

        ResultMap r2 = Query.eval(
                new TemporalJoin(
                        flights2,
                        flights2
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        assertEquals(r2, nr);
    }
}
