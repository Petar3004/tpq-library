import Data.Graph;
import Data.Query;
import Data.ResultMap;
import Data.Window;
import Operators.Binary.TemporalJoin;
import Operators.Nullary.Read;
import Operators.Operator;
import Operators.Unary.Converse;
import Operators.Unary.Exists;
import Operators.Unary.Select.Atom;
import Operators.Unary.Select.Select;
import Operators.Unary.Stretch.HStretch;
import Operators.Unary.Stretch.VStretch;
import Utils.Utils.NodePair;
import Utils.Interval;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryTest {

    @Test
    void testConferenceQuery() {
        Graph conf = new Graph();
        conf.fetchTxtRelations("src/test/resources/conferences_1");

        Operator attends = new Read("attends", conf);

        ResultMap r1 = Query.eval(
                new TemporalJoin(
                        new TemporalJoin(
                                new TemporalJoin(
                                        new VStretch(
                                                new Converse(
                                                        new Select(
                                                                attends,
                                                                new Atom(Atom.Node.N2, "ISWC")
                                                        )
                                                ),
                                                new Interval(3, 4)
                                        ),
                                        attends
                                ),
                                new Converse(
                                        attends
                                )
                        ),
                        new Exists(
                                new Select(
                                        new HStretch(
                                                new Read("tests", conf),
                                                new Interval(0, 7)
                                        ),
                                        new Atom(Atom.Node.N2, "positive")
                                )
                        )
                ),
                Query.QueryStrategy.MINIMAL,
                false,
                false
        );

        ResultMap rn = new ResultMap();
        rn.results.put(new NodePair("ISWC", "Bob"), List.of(
                new Window(
                        new Interval(100, 101),
                        new Interval(3, 4),
                        new Interval(104, 104)
                )
        ));

        assertEquals(r1, rn);
    }

//    @Test
//    void testJoinQuery() {
//        Graph g = new Graph();
//        g.fetchTsv("src/test/resources/test-flights2.tsv", 2);
//
//        Operator flights = new Read("travels", g);
//
//        ResultMap r = Query.eval(
//                new TemporalJoin(
//                        flights,
//                        new TemporalJoin(
//                                flights,
//                                new TemporalJoin(
//                                        flights,
//                                        flights
//                                )
//                        )
//                ),
//                Query.QueryStrategy.INTERMEDIATE,
//                false
//        );

//        assertEquals();
//    }
}
