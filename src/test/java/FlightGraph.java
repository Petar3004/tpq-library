import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import Data.Graph;
import Data.ResultMap;
import Data.Window;
import Utils.Interval;
import Utils.Utils.*;

public class FlightGraph extends Graph {
    private final Pattern PREFIX = Pattern.compile("^\\d{8}.*");

    public void fetchTsv(String filePath, int days, int nodePercentage) {
        Map<FlightKey, Window> flightKeyToWindow = new HashMap<>();
        ResultMap flights = new ResultMap();
        Path path = Path.of(filePath);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line = reader.readLine();
            while (line != null && !isValid(line)) {
                line = reader.readLine();
            }

            // PHASE 1: Parse the entire file and aggregate windows per FlightKey
            while (line != null) {
                String[] row = line.split("[ \\t\\n]++");
                Utils.Utils.NodePair nodes = new Utils.Utils.NodePair(row[2], row[4]);

                if (nodes.n1().equals("\\N") || nodes.n2().equals("\\N")) {
                    line = reader.readLine();
                    continue;
                }

                int flightNum = Integer.parseInt(row[1]);
                Utils.Utils.FlightKey flightKey = new Utils.Utils.FlightKey(flightNum, nodes);

                Interval i = getArrDep(line);
                if (i == null) {
                    line = reader.readLine();
                    continue;
                }

                Window w = new Window(i);
                Window old = flightKeyToWindow.get(flightKey);

                if (old != null) {
                    updateWindow(old, w);
                } else {
                    // Initialize the window tracking map
                    flightKeyToWindow.put(flightKey, w);
                }

                line = reader.readLine();
            }

            // PHASE 2: Add base windows to flights and generate shifted copies for ALL flight keys
            Set<Utils.Utils.NodePair> uniqueNodePairs = new HashSet<>();
            for (Utils.Utils.FlightKey key : flightKeyToWindow.keySet()) {
                uniqueNodePairs.add(key.nodes());
            }

            double clampedPercentage = Math.min(Math.max(nodePercentage, 0), 100);
            int targetCount = (int) Math.ceil(uniqueNodePairs.size() * (clampedPercentage / 100.0));

            Set<Utils.Utils.NodePair> selectedNodePairs = new HashSet<>();
            int count = 0;
            for (Utils.Utils.NodePair node : uniqueNodePairs) {
                if (count >= targetCount) break;
                selectedNodePairs.add(node);
                count++;
            }

            for (Map.Entry<Utils.Utils.FlightKey, Window> entry : flightKeyToWindow.entrySet()) {
                Utils.Utils.FlightKey key = entry.getKey();
                Utils.Utils.NodePair nodes = key.nodes();

                // Skip if this NodePair was not selected in the exact sample
                if (!selectedNodePairs.contains(nodes)) {
                    continue;
                }

                Window baseWindow = entry.getValue();

                // Add the base aggregated window
                flights.results.computeIfAbsent(nodes, k -> new ArrayList<>()).add(baseWindow);

                // Add shifted copies across all days
                List<Window> shifted = copyAndShift(baseWindow, days);
                flights.results.get(nodes).addAll(shifted);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        graph.put("travels", flights);
    }

    private boolean isValid(String line) {
        return PREFIX.matcher(line).matches();
    }

    private List<Window> copyAndShift(Window w, int days) {
        List<Window> l = new ArrayList<>();

        if((w.sigma.uBound() <= 1440 && w.tau.lBound() > 1440) ||
                (w.sigma.lBound() <= 1440 && w.sigma.uBound() > 1440) ||
                (w.tau.lBound() <= 1440 && w.tau.uBound() > 1440)) {
            days--;
        }

        for(int i = 0; i < days - 1; i++) {
            l.add(new Window(
                    new Interval(w.sigma.lBound() + (i + 1) * 1440, w.sigma.uBound() + (i + 1) * 1440),
                    w.delta,
                    new Interval(w.tau.lBound() + (i + 1) * 1440, w.tau.uBound() + (i + 1) * 1440)
            ));
        }
        return l;
    }

    private void updateWindow(Window old, Window w) {
        old.sigma = updateInterval(w.sigma, old.sigma);
        old.delta = updateInterval(w.delta, old.delta);
        old.tau = updateInterval(w.tau, old.tau);
    }

    private Interval updateInterval(Interval inter, Interval intOld) {
        int newLBound = Math.min(inter.lBound(), intOld.lBound());
        int newUBound = Math.max(inter.uBound(), intOld.uBound());
        return new Interval(newLBound, newUBound);
    }

    public Interval getArrDep(String rawRow) {
        // Clean up extra whitespace and split the row by spaces/tabs
        String[] tokens = rawRow.trim().split("\\s+");

        // Basic validation: skip header row or malformed data
        if (tokens.length < 16 || tokens[0].equals("ER")) {
            return null;
        }

        try {
            // Extract the minute-of-day (0 to 1439)
            int adMinutes = convertTimeToMinutes(tokens[11]);
            int aaMinutes = convertTimeToMinutes(tokens[15]);
            // If the flight crosses midnight, shift the arrival.
            if(aaMinutes < adMinutes) {
                aaMinutes += 1440;
            }
            int sdMinutes = convertTimeToMinutes(tokens[9]);

            // Handle outliers
            if(Math.abs(signedDelay(sdMinutes, adMinutes)) > 120) {
                return null;
            }
            return new Interval(adMinutes, aaMinutes);

        } catch (Exception e) {
            // Handle parsing errors for edge cases or corrupted rows gracefully
            System.err.println("Error processing row: " + rawRow + " -> " + e.getMessage());
            return null;
        }
    }

    private int signedDelay(int scheduled, int actual) {
        int delay = actual - scheduled;

        if (delay < -720) {
            delay += 1440;
        } else if (delay > 720) {
            delay -= 1440;
        }

        return delay;
    }

    private int convertTimeToMinutes(String timeStr) {
        // Split the time string by the colon delimiter
        String[] timeParts = timeStr.split(":");

        int hours = Integer.parseInt(timeParts[0]);
        int minutes = Integer.parseInt(timeParts[1]);

        return (hours * 60) + minutes;
    }
}