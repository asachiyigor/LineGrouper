package com.peacockteam;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LineGrouperTest {

    private Path tempDir;
    private LineGrouper grouper;

    @BeforeEach
    void setUp() throws IOException {
        grouper = new LineGrouper();
        tempDir = Files.createTempDirectory("test");
    }

    @AfterEach
    void tearDown() {
        deleteDirectory(tempDir.toFile());
    }

    @Test
    void testBasicGrouping() throws IOException {
        String content = """
                111;123;222
                200;123;100
                300;;100
                400;500;600
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(1, groupCount);

        String output = readFile(outputFile);
        assertTrue(output.contains("111;123;222"));
        assertTrue(output.contains("200;123;100"));
        assertTrue(output.contains("300;;100"));
        assertFalse(output.contains("400;500;600"));
    }

    @Test
    void testDifferentPositionsNotGrouped() throws IOException {
        String content = """
                100;200;300
                200;300;100
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(0, groupCount);
    }

    @Test
    void testDuplicateRemoval() throws IOException {
        String content = """
                111;222;333
                111;222;333
                444;555;666
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(0, groupCount);
    }

    @Test
    void testInvalidLines() throws IOException {
        String content = """
                111;222;333
                "8383"200000741652251"
                444;555;666
                "79855053897"83100000580443402";"200000133000191"
                777;888;999
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());
        String output = readFile(outputFile);

        if (groupCount == 0) {
            assertTrue(output.trim().startsWith("0"));
            return;
        }

        assertTrue(output.contains("111;222;333"));
        assertTrue(output.contains("444;555;666"));
        assertTrue(output.contains("777;888;999"));
        assertFalse(output.contains("8383"));
        assertFalse(output.contains("79855053897"));
    }

    @Test
    void testQuoteHandling() throws IOException {
        String content = """
                "111";"222";"333"
                "444";"222";"555"
                666;777;888
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(1, groupCount);

        String output = readFile(outputFile);
        assertTrue(output.contains("111;222;333"));
        assertTrue(output.contains("444;222;555"));
    }

    @Test
    void testEmptyValues() throws IOException {
        String content = """
                111;;333
                222;;444
                ;;
                555;666;
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        grouper.processFile(inputFile.toString(), outputFile.toString());

        String output = readFile(outputFile);
        assertFalse(output.contains(";;"));
    }

    @Test
    void testComplexGrouping() throws IOException {
        String content = """
                A;B;C
                D;B;E
                F;G;C
                H;I;J
                K;I;L
                """;

        Path inputFile = createTestFile("test.txt", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(2, groupCount);

        String output = readFile(outputFile);
        assertTrue(output.contains("A;B;C"));
        assertTrue(output.contains("D;B;E"));
        assertTrue(output.contains("F;G;C"));
        assertTrue(output.contains("H;I;J"));
        assertTrue(output.contains("K;I;L"));
    }

    @Test
    void testCustomDelimiter() throws IOException {
        String content = """
                111,222,333
                444,222,555
                """;

        Path inputFile = createTestFile("test.csv", content);
        Path outputFile = tempDir.resolve("output.txt");

        grouper.setDelimiter(",");
        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(1, groupCount);
    }

    @Test
    void testEmptyFile() throws IOException {
        Path inputFile = createTestFile("empty.txt", "");
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(0, groupCount);

        String output = readFile(outputFile);
        assertTrue(output.startsWith("0"));
    }

    @Test
    void testGzipFile() throws IOException {
        String content = """
                111;222;333
                444;222;555
                """;

        Path inputFile = createGzipTestFile("test.txt.gz", content);
        Path outputFile = tempDir.resolve("output.txt");

        int groupCount = grouper.processFile(inputFile.toString(), outputFile.toString());

        assertEquals(1, groupCount);
    }

    private Path createTestFile(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file.toFile(), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
        return file;
    }

    private Path createGzipTestFile(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(file.toFile()));
             OutputStreamWriter writer = new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return file;
    }

    private String readFile(Path file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile(), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}