package com.peacockteam;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class LineGrouper {
    private static final Pattern INVALID_LINE_PATTERN = Pattern.compile(".*\"\\d+\"\\d+\".*");
    private char delimiterChar = ';';

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java -jar line-grouper.jar <input_file_path> [output_file_path]");
            System.exit(1);
        }

        var inputPath = args[0];
        var outputPath = args.length > 1 ? args[1] : "result.txt";
        var delimiter = detectDelimiter(inputPath);
        var startTime = System.currentTimeMillis();

        try {
            var grouper = new LineGrouper();
            grouper.setDelimiter(delimiter);
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

    private static String detectDelimiter(String filePath) {
        return ";";
    }

    public void setDelimiter(String delimiter) {
        this.delimiterChar = delimiter.charAt(0);
    }

    public int processFile(String filePath, String outputPath) throws IOException {
        var result = processFileInOnePass(filePath);
        var groups = buildGroupsFromMemory(result.allLines, result.groupingValues);
        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));
        writeResults(groups, outputPath);
        return groups.size();
    }

    private ProcessResult processFileInOnePass(String filePath) throws IOException {
        var valueFrequencies = new HashMap<String, Integer>(200000);
        var allLines = new ArrayList<String>(1000000);
        var lineSet = new HashSet<String>(1000000);

        try (var reader = createReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var trimmedLine = line.strip();
                if (!trimmedLine.isEmpty() && isValidLine(trimmedLine)) {
                    var normalizedLine = normalizeLine(trimmedLine);

                    if (lineSet.add(normalizedLine)) {
                        allLines.add(normalizedLine);
                        parseLineAndCountValues(normalizedLine, valueFrequencies);
                    }
                }
            }
        }

        var groupingValues = new HashSet<String>(valueFrequencies.size() / 4);
        for (var entry : valueFrequencies.entrySet()) {
            if (entry.getValue() > 1) {
                groupingValues.add(entry.getKey());
            }
        }

        return new ProcessResult(allLines, groupingValues);
    }

    private void parseLineAndCountValues(String line, Map<String, Integer> valueFrequencies) {
        int start = 0;
        int length = line.length();

        while (start < length) {
            int end = line.indexOf(delimiterChar, start);
            if (end == -1) {
                end = length;
            }

            if (end > start) {
                String value = extractValue(line, start, end);
                if (!value.isEmpty()) {
                    valueFrequencies.merge(value, 1, Integer::sum);
                }
            }

            start = end + 1;
        }
    }

    private String extractValue(String line, int start, int end) {
        while (start < end && Character.isWhitespace(line.charAt(start))) {
            start++;
        }

        while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }

        if (start >= end) {
            return "";
        }

        return line.substring(start, end);
    }

    private List<List<String>> buildGroupsFromMemory(List<String> allLines, Set<String> groupingValues) {
        if (groupingValues.isEmpty()) {
            return new ArrayList<>();
        }

        var unionFind = new UnionFind(allLines.size());
        var valueToPositions = new HashMap<String, List<Integer>>(groupingValues.size() * 10);

        for (int i = 0; i < allLines.size(); i++) {
            parseLineForGrouping(allLines.get(i), i, groupingValues, valueToPositions);
        }

        for (var positions : valueToPositions.values()) {
            if (positions.size() > 1) {
                var first = positions.get(0);
                for (int j = 1; j < positions.size(); j++) {
                    unionFind.union(first, positions.get(j));
                }
            }
        }

        var rootToGroup = new HashMap<Integer, List<String>>();
        for (int i = 0; i < allLines.size(); i++) {
            var root = unionFind.find(i);
            rootToGroup.computeIfAbsent(root, k -> new ArrayList<>()).add(allLines.get(i));
        }

        var result = new ArrayList<List<String>>();
        for (var group : rootToGroup.values()) {
            if (group.size() > 1) {
                result.add(group);
            }
        }

        return result;
    }

    private void parseLineForGrouping(String line, int lineIndex, Set<String> groupingValues,
                                      Map<String, List<Integer>> valueToPositions) {
        int start = 0;
        int length = line.length();
        int col = 0;

        while (start < length) {
            int end = line.indexOf(delimiterChar, start);
            if (end == -1) {
                end = length;
            }

            if (end > start) {
                String value = extractValue(line, start, end);
                if (!value.isEmpty() && groupingValues.contains(value)) {
                    String key = value + ":" + col;
                    valueToPositions.computeIfAbsent(key, k -> new ArrayList<>()).add(lineIndex);
                }
            }

            col++;
            start = end + 1;
        }
    }

    private void writeResults(List<List<String>> groups, String outputPath) throws IOException {
        try (var writer = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8), 131072)) {
            writer.write(String.valueOf(groups.size()));
            writer.newLine();
            writer.newLine();

            for (int i = 0; i < groups.size(); i++) {
                writer.write("Группа ");
                writer.write(String.valueOf(i + 1));
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
        boolean isGzipped = filePath.toLowerCase().endsWith(".gz") ||
                filePath.toLowerCase().endsWith(".gzip");

        if (isGzipped) {
            var inputStream = new FileInputStream(filePath);
            return new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(inputStream), StandardCharsets.UTF_8), 131072);
        } else {
            return new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8), 131072);
        }
    }

    private boolean isValidLine(String line) {
        if (line.contains("\"")) {
            if (INVALID_LINE_PATTERN.matcher(line).matches()) {
                return false;
            }
        }

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != delimiterChar && c != '"' && !Character.isWhitespace(c)) {
                return true;
            }
        }

        return false;
    }

    private String normalizeLine(String line) {
        if (!line.contains("\"")) {
            return line.strip();
        }

        return line.replace("\"", "").strip();
    }

    private record ProcessResult(List<String> allLines, Set<String> groupingValues) {}

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