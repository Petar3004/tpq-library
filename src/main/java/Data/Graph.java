package Data;

import Utils.Interval;
import Utils.Utils.NodePair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static Utils.Utils.NEG_INF;
import static Utils.Utils.POS_INF;

public class Graph {

    // Maps each relation / edge label to the corresponding set of results.
    public Map<String, ResultMap> graph = new HashMap<>();

    /**
     * Opens a connection to the SQL database identified by the given URL.
     */
    private static Connection conn(String URL) throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /**
     * Fetches a single relation from an SQL database and converts it into a ResultMap.
     */
    public static ResultMap fetchSQLRelation(String relName, String URL) throws SQLException {
        String query = "SELECT * FROM \"" + relName + "\"";
        Statement stmt = conn(URL).createStatement();
        ResultSet rs = stmt.executeQuery(query);

        return parseSQLTable(rs);
    }

    /**
     * Converts the rows of an SQL table into a ResultMap.
     *
     * Unary relations are expected to contain one node column followed by six interval-bound columns.
     * Binary relations contain two node columns followed by the six interval-bound columns.
     */
    private static ResultMap parseSQLTable(ResultSet rs) throws SQLException {
        boolean isUnary = rs.getMetaData().getColumnCount() == 7;

        ResultMap r = new ResultMap();
        NodePair nodes;
        Window w;

        while (rs.next()) {
            if (isUnary) {
                // Unary relations are represented as pairs (v, v).
                nodes = new NodePair(
                        rs.getString(1),
                        rs.getString(1)
                );

                w = new Window(
                        new Interval(rs.getInt(2), rs.getInt(3)),
                        new Interval(rs.getInt(4), rs.getInt(5)),
                        new Interval(rs.getInt(6), rs.getInt(7))
                );
            } else {
                // Binary relations explicitly provide source and target nodes.
                nodes = new NodePair(
                        rs.getString(1),
                        rs.getString(2)
                );

                w = new Window(
                        new Interval(rs.getInt(3), rs.getInt(4)),
                        new Interval(rs.getInt(5), rs.getInt(6)),
                        new Interval(rs.getInt(7), rs.getInt(8))
                );
            }

            // Multiple temporal windows may belong to the same node pair.
            r.results
                    .computeIfAbsent(nodes, k -> new ArrayList<>())
                    .add(w);
        }

        return r;
    }

    /**
     * Fetches all SQL tables in the database and stores each one as a separate relation in the graph.
     */
    public void fetchSQLRelations(String URL) throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        DatabaseMetaData md = conn.getMetaData();

        String[] types = {"TABLE"};
        ResultSet rs = md.getTables(null, null, "%", types);

        while (rs.next()) {
            String relName = rs.getString("TABLE_NAME");
            graph.put(relName, fetchSQLRelation(relName, URL));
        }
    }

    /**
     * Reads a single relation from a text file.
     *
     * Lines beginning with "//" are treated as comments and ignored.
     */
    public ResultMap fetchTxtRelation(String relName, String dirPath) {
        ResultMap r = new ResultMap();

        try (
                BufferedReader reader =
                        Files.newBufferedReader(Path.of(dirPath + "/" + relName))
        ) {
            String line = reader.readLine();

            while (line != null) {
                // Ignore comment lines.
                if (line.startsWith("//")) {
                    line = reader.readLine();
                    continue;
                }

                NodePair nodes = getNodes(line);
                Window w = getWindowTxt(line);

                // Store all windows associated with the same node pair.
                r.results
                        .computeIfAbsent(nodes, k -> new ArrayList<>())
                        .add(w);

                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return r;
    }

    /**
     * Extracts the two node identifiers from a colon-separated text row.
     */
    private NodePair getNodes(String line) {
        String[] strValues = line.split(":");

        return new NodePair(
                strValues[0],
                strValues[1]
        );
    }

    /**
     * Extracts the six interval bounds from a colon-separated text row and constructs the corresponding temporal window.
     */
    private Window getWindowTxt(String line) {
        String[] strValues = line.split(":");
        int[] parsedValues = new int[6];

        for (int i = 0; i < 6; i++) {
            parsedValues[i] = parseVal(strValues[i + 2]);
        }

        return new Window(
                new Interval(parsedValues[0], parsedValues[1]),
                new Interval(parsedValues[2], parsedValues[3]),
                new Interval(parsedValues[4], parsedValues[5])
        );
    }

    /**
     * Parses an interval endpoint.
     *
     * "-inf" and "+inf" are represented using the minimum and maximum integer values respectively.
     */
    private int parseVal(String val) {
        int i;

        switch (val) {
            case "-inf" -> i = NEG_INF;
            case "+inf" -> i = POS_INF;
            default -> i = Integer.parseInt(val);
        }

        return i;
    }

    /**
     * Reads all relation files from a directory and stores them in the graph.
     *
     * Files whose names begin with "ex_" are ignored.
     */
    public void fetchTxtRelations(String dirPath) {
        File dir = new File(dirPath);
        File[] files = dir.listFiles();

        if (files == null) {
            System.err.println("No files found in " + dirPath);
            return;
        }

        for (File f : files) {
            String fileName = f.getName();

            if (f.isFile() && !fileName.startsWith("ex_")) {
                // Remove the file extension before using the filename as the relation / edge label.
                String relName = fileName.substring(0, fileName.length() - 4);

                graph.put(
                        relName,
                        fetchTxtRelation(fileName, dirPath)
                );
            }
        }
    }

    /**
     * Prints every relation in the graph and its corresponding results.
     */
    public void print() {
        boolean first = true;

        for (Map.Entry<String, ResultMap> r : graph.entrySet()) {
            if (!first) {
                System.out.println();
            }

            System.out.println("Edge Label: " + r.getKey());
            r.getValue().print();

            first = false;
        }
    }

    /**
     * Returns the total number of node pairs across all relations.
     */
    public int pairCount() {
        return graph.values()
                .stream()
                .mapToInt(ResultMap::pairCount)
                .sum();
    }

    /**
     * Returns the total number of temporal windows across all relations.
     */
    public int windowCount() {
        return graph.values()
                .stream()
                .mapToInt(ResultMap::windowCount)
                .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Graph graph1 = (Graph) o;

        return Objects.equals(graph, graph1.graph);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(graph);
    }
}