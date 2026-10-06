package Data;

import Utils.Interval;
import Utils.Utils.FlightKey;
import Utils.Utils.NodePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class FlightGraph extends Graph {

    // Valid data rows begin with an eight-digit date.
    private static final Pattern PREFIX = Pattern.compile("^\\d{8}.*");

    /**
     * Reads flight data from a TSV-like file and constructs the "travels" relation.
     *
     * Flight records are first aggregated by flight number and node pair.
     * A configurable percentage of unique node pairs is then retained, and
     * each aggregated temporal window is replicated across the requested number of days.
     *
     * @param filePath       path to the input flight file
     * @param days           number of days over which windows are replicated
     * @param nodePercentage percentage of unique node pairs to retain
     */
    public void fetchTsv(String filePath, int days, int nodePercentage) {
        Map<FlightKey, Window> flightKeyToWindow = new HashMap<>();

        ResultMap flights = new ResultMap();
        Path path = Path.of(filePath);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line = reader.readLine();

            // Skip headers or other leading non-data rows.
            while (line != null && !isValid(line)) {
                line = reader.readLine();
            }

            /*
             * Phase 1:
             * Parse the complete input and aggregate all observations belonging to the same flight number and node pair.
             */
            while (line != null) {
                String[] row = line.split("[ \\t\\n]++");

                NodePair nodes = new NodePair(row[2], row[4]);

                // Ignore records with missing endpoint identifiers.
                if (nodes.n1().equals("\\N") || nodes.n2().equals("\\N")) {
                    line = reader.readLine();
                    continue;
                }

                int flightNum = Integer.parseInt(row[1]);

                FlightKey flightKey = new FlightKey(flightNum, nodes);

                Interval arrDep = getArrDep(line);

                // Ignore malformed or filtered-out flight records.
                if (arrDep == null) {
                    line = reader.readLine();
                    continue;
                }

                Window newWindow = new Window(arrDep);

                Window existingWindow = flightKeyToWindow.get(flightKey);

                if (existingWindow != null) {
                    /*
                     * Expand the existing aggregate window so that it
                     * covers the newly observed flight instance.
                     */
                    updateWindow(existingWindow, newWindow);
                } else {
                    flightKeyToWindow.put(flightKey, newWindow);
                }

                line = reader.readLine();
            }

            /*
             * Phase 2:
             * Select the requested proportion of unique node pairs and
             * construct the final temporal relation.
             */
            Set<NodePair> uniqueNodePairs = new HashSet<>();

            for (FlightKey key : flightKeyToWindow.keySet()) {
                uniqueNodePairs.add(key.nodes());
            }

            // Clamp the requested percentage to the range [0, 100].
            double clampedPercentage = Math.min(Math.max(nodePercentage, 0), 100);

            int targetCount = (int) Math.ceil(uniqueNodePairs.size() * (clampedPercentage / 100.0));

            /*
             * Select exactly targetCount node pairs.
             */
            Set<NodePair> selectedNodePairs = new HashSet<>();

            int count = 0;

            for (NodePair node : uniqueNodePairs) {
                if (count >= targetCount) {
                    break;
                }

                selectedNodePairs.add(node);
                count++;
            }

            /*
             * Add each selected base window and replicate it over the requested number of days.
             */
            for (Map.Entry<FlightKey, Window> entry: flightKeyToWindow.entrySet()) {
                FlightKey key = entry.getKey();
                NodePair nodes = key.nodes();

                if (!selectedNodePairs.contains(nodes)) {
                    continue;
                }

                Window baseWindow = entry.getValue();

                flights.results.computeIfAbsent(
                                nodes,
                                k -> new ArrayList<>()
                        )
                        .add(baseWindow);

                List<Window> shifted = copyAndShift(
                        baseWindow,
                        days
                );

                flights.results.get(nodes).addAll(shifted);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Store the constructed temporal relation under the "travels" label.
        graph.put("travels", flights);
    }

    /**
     * Tests whether a line appears to contain a flight-data record.
     */
    private boolean isValid(String line) {
        return PREFIX.matcher(line).matches();
    }

    /**
     * Creates day-shifted copies of a temporal window.
     *
     * Each copy is shifted forward by a multiple of 1440 minutes.
     * Windows that already span into the following day require one fewer replicated copy.
     *
     * @param w    base window
     * @param days number of days to represent
     * @return shifted copies of the input window
     */
    private List<Window> copyAndShift(Window w, int days) {
        List<Window> shifted = new ArrayList<>();

        /*
         * If the base window already crosses the first day boundary,
         * it already occupies part of the following day.
         */
        if ((w.sigma.uBound() <= 1440 && w.tau.lBound() > 1440)
                || (w.sigma.lBound() <= 1440
                && w.sigma.uBound() > 1440)
                || (w.tau.lBound() <= 1440
                && w.tau.uBound() > 1440)) {

            days--;
        }

        for (int i = 0; i < days - 1; i++) {
            int offset = (i + 1) * 1440;

            shifted.add(new Window(
                    new Interval(
                            w.sigma.lBound() + offset,
                            w.sigma.uBound() + offset
                    ),
                    w.delta,
                    new Interval(
                            w.tau.lBound() + offset,
                            w.tau.uBound() + offset
                    ))
            );
        }

        return shifted;
    }

    /**
     * Expands an aggregated window so that it contains the bounds of a newly observed window.
     */
    private void updateWindow(Window old, Window w) {
        old.sigma = updateInterval(w.sigma, old.sigma);
        old.delta = updateInterval(w.delta, old.delta);
        old.tau = updateInterval(w.tau, old.tau);
    }

    /**
     * Returns the smallest interval containing both input intervals.
     */
    private Interval updateInterval(Interval current, Interval existing) {
        int newLBound = Math.min(current.lBound(), existing.lBound() );
        int newUBound = Math.max(current.uBound(), existing.uBound());

        return new Interval(newLBound, newUBound);
    }

    /**
     * Extracts actual departure and arrival times from a raw flight row.
     *
     * Times are converted to minutes from midnight. If arrival occurs after midnight,
     * 1440 minutes are added so that the interval remains ordered.
     *
     * Records with an absolute departure delay greater than two hours are treated as outliers and discarded.
     *
     * @param rawRow raw input row
     * @return interval [actual departure, actual arrival], or null if the row is invalid or filtered out
     */
    public Interval getArrDep(String rawRow) {
        String[] tokens = rawRow.trim().split("\\s+");

        // Reject malformed rows and header records.
        if (tokens.length < 16 || tokens[0].equals("ER")) {
            return null;
        }

        try {
            // Actual departure and arrival times.
            int actualDeparture = convertTimeToMinutes(tokens[11]);
            int actualArrival = convertTimeToMinutes(tokens[15]);

            /*
             * If the arrival clock time is earlier than the departure
             * clock time, the flight crossed midnight.
             */
            if (actualArrival < actualDeparture) {
                actualArrival += 1440;
            }

            int scheduledDeparture = convertTimeToMinutes(tokens[9]);

            // Discard departure-delay outliers larger than two hours.
            if (Math.abs(signedDelay(scheduledDeparture, actualDeparture)) > 120) {
                return null;
            }

            return new Interval(
                    actualDeparture,
                    actualArrival
            );

        } catch (Exception e) {
            /*
             * Malformed or corrupted rows are ignored rather than terminating the entire import.
             */
            System.err.println("Error processing row: " + rawRow + " -> " + e.getMessage());

            return null;
        }
    }

    /**
     * Computes the signed delay between scheduled and actual time while accounting for midnight wrap-around.
     */
    private int signedDelay(int scheduled, int actual) {
        int delay = actual - scheduled;

        if (delay < -720) {
            delay += 1440;
        } else if (delay > 720) {
            delay -= 1440;
        }

        return delay;
    }

    /**
     * Converts a time formatted as HH:mm into minutes from midnight.
     */
    private int convertTimeToMinutes(String timeStr) {
        String[] timeParts = timeStr.split(":");

        int hours = Integer.parseInt(timeParts[0]);
        int minutes = Integer.parseInt(timeParts[1]);

        return hours * 60 + minutes;
    }
}