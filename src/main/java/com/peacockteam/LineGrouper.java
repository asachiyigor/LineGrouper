package com.peacockteam;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class LineGrouper {
    private static final Pattern INVALID_LINE_PATTERN = Pattern.compile(".*\"\\d+\"\\d+\".*");

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java -jar line-grouper.jar <input_file_path> [output_file_path]");
            System.exit(1);
        }

        var inputPath = args[0];
        var outputPath = args.length > 1 ? args[1] : "result.txt";
        var startTime = System.currentTimeMillis();

        try {
            var grouper = new LineGrouper();
            var groupCount = grouper.processFile(inputPath, outputPath);

            var endTime = System.currentTimeMillis();
            var executionTime = (endTime - startTime) / 1000.0;

            System.out.println("Groups with more than one element: " + groupCount);
            System.out.println("Execution time: " + executionTime + " seconds");

        } catch (Exception e) {
            System.err.println("Error processing file: " + e.getMessage());
            System.exit(1);
        }
    }

    public int processFile(String filePath, String outputPath) throws IOException {
        var groupingValues = findGroupingValues(filePath);
        return buildGroupsAndWriteResults(filePath, groupingValues, outputPath);
    }

    private Set<String> findGroupingValues(String filePath) throws IOException {
        var valueFrequencies = new HashMap<String, Integer>();

        try (var reader = createReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var trimmedLine = line.strip();
                if (!trimmedLine.isEmpty() && isValidLine(trimmedLine)) {
                    var normalizedLine = normalizeLine(trimmedLine);
                    var parts = normalizedLine.split(";", -1);

                    for (var part : parts) {
                        var value = part.strip();
                        if (!value.isEmpty()) {
                            valueFrequencies.merge(value, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        var groupingValues = new HashSet<String>();
        for (var entry : valueFrequencies.entrySet()) {
            if (entry.getValue() > 1) {
                groupingValues.add(entry.getKey());
            }
        }

        return groupingValues;
    }

    private int buildGroupsAndWriteResults(String filePath, Set<String> groupingValues, String outputPath) throws IOException {
        var allLines = new ArrayList<String>();
        var lineSet = new HashSet<String>();

        try (var reader = createReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var trimmedLine = line.strip();
                if (!trimmedLine.isEmpty() && isValidLine(trimmedLine)) {
                    var normalizedLine = normalizeLine(trimmedLine);
                    if (!lineSet.contains(normalizedLine)) {
                        lineSet.add(normalizedLine);
                        allLines.add(normalizedLine);
                    }
                }
            }
        }

        var groups = groupLines(allLines, groupingValues);
        var sortedGroups = sortGroupsByPhoneCount(groups);
        writeResultsToFile(sortedGroups, outputPath);
        return sortedGroups.size();
    }

    private List<List<String>> groupLines(List<String> lines, Set<String> groupingValues) {
        var unionFind = new UnionFind(lines.size());
        var valueToPositions = new HashMap<String, List<Integer>>();

        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var parts = line.split(";", -1);

            for (int col = 0; col < parts.length; col++) {
                var value = parts[col].strip();
                if (!value.isEmpty() && groupingValues.contains(value)) {
                    var key = value + ":" + col;
                    valueToPositions.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
                }
            }
        }

        for (var positions : valueToPositions.values()) {
            if (positions.size() > 1) {
                for (int i = 1; i < positions.size(); i++) {
                    unionFind.union(positions.get(0), positions.get(i));
                }
            }
        }

        var rootToGroup = new HashMap<Integer, List<String>>();
        for (int i = 0; i < lines.size(); i++) {
            var root = unionFind.find(i);
            rootToGroup.computeIfAbsent(root, k -> new ArrayList<>()).add(lines.get(i));
        }

        var result = new ArrayList<List<String>>();
        for (var group : rootToGroup.values()) {
            if (group.size() > 1) {
                result.add(group);
            }
        }

        return result;
    }

    private List<List<String>> sortGroupsByPhoneCount(List<List<String>> groups) {
        var groupsWithCounts = new ArrayList<GroupWithPhoneCount>();

        for (var group : groups) {
            var uniqueNumbers = new HashSet<String>();
            for (var line : group) {
                var parts = line.split(";", -1);
                for (var part : parts) {
                    var number = part.strip();
                    if (!number.isEmpty()) {
                        uniqueNumbers.add(number);
                    }
                }
            }

            if (uniqueNumbers.size() > 1) {
                groupsWithCounts.add(new GroupWithPhoneCount(group, uniqueNumbers.size()));
            }
        }

        groupsWithCounts.sort((a, b) -> Integer.compare(b.phoneCount, a.phoneCount));

        var result = new ArrayList<List<String>>();
        for (var gwc : groupsWithCounts) {
            result.add(gwc.group);
        }

        return result;
    }

    private void writeResultsToFile(List<List<String>> groups, String outputPath) throws IOException {
        try (var writer = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            writer.write(String.valueOf(groups.size()));
            writer.newLine();
            writer.newLine();
            for (int i = 0; i < groups.size(); i++) {
                writer.write("Группа " + (i + 1));
                writer.newLine();

                for (var line : groups.get(i)) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.newLine();
            }
        }
    }

    private BufferedReader createReader(String filePath) throws IOException {
        InputStream inputStream;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            inputStream = new URL(filePath).openStream();
        } else {
            inputStream = new FileInputStream(filePath);
        }

        return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(inputStream), StandardCharsets.UTF_8));
    }

    private boolean isValidLine(String line) {
        if (INVALID_LINE_PATTERN.matcher(line).matches()) {
            return false;
        }

        var cleanLine = line.replace("\"", "");
        var parts = cleanLine.split(";", -1);

        for (var part : parts) {
            if (!part.isBlank()) {
                return true;
            }
        }

        return false;
    }

    private String normalizeLine(String line) {
        return line.replace("\"", "").strip();
    }

    private record GroupWithPhoneCount(List<String> group, int phoneCount) {
    }

    private static class UnionFind {
        private final int[] parent;
        private final int[] rank;

        public UnionFind(int size) {
            parent = new int[size];
            rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }

        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        public void union(int x, int y) {
            var rootX = find(x);
            var rootY = find(y);

            if (rootX != rootY) {
                if (rank[rootX] < rank[rootY]) {
                    parent[rootX] = rootY;
                } else if (rank[rootX] > rank[rootY]) {
                    parent[rootY] = rootX;
                } else {
                    parent[rootY] = rootX;
                    rank[rootX]++;
                }
            }
        }
    }
}