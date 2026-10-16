package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.LogPage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

class NodeLogReaderTest {
    @TempDir
    Path root;

    @Test
    void preservesUtf8ByteCursorsAndDefersIncompleteLines() throws Exception {
        Path log = root.resolve("node.log");
        Files.writeString(log, "Привет\r\nsecond\npartial", StandardCharsets.UTF_8);
        LogPage first = NodeLogReader.read(log, 0, 1);
        assertEquals(List.of("Привет"), first.getLines());
        assertEquals("Привет\r\n".getBytes(StandardCharsets.UTF_8).length, first.getNextOffset());
        LogPage second = NodeLogReader.read(log, first.getNextOffset(), 10);
        assertEquals(List.of("second"), second.getLines());
        assertTrue(NodeLogReader.read(log, second.getNextOffset(), 10).getLines().isEmpty());
        Files.writeString(log, "\n", StandardOpenOption.APPEND);
        assertEquals(List.of("partial"),
                     NodeLogReader.read(log, second.getNextOffset(), 10).getLines());
    }

    @Test
    void restartsAtTheBeginningWhenTheLogWasTruncated() throws Exception {
        Path log = root.resolve("node.log");
        Files.writeString(log, "new\n");
        LogPage page = NodeLogReader.read(log, 100, 10);
        assertTrue(page.isRotated());
        assertEquals(List.of("new"), page.getLines());
        assertEquals(4, page.getNextOffset());
    }

    @Test
    void advancesPastAnOversizedFragmentWithoutLoadingTheWholeLog() throws Exception {
        Path log = root.resolve("node.log");
        Files.writeString(log, "x".repeat(65536) + "tail\n");
        LogPage first = NodeLogReader.read(log, 0, 10);
        assertEquals(List.of("[Oversized log line skipped]"), first.getLines());
        assertEquals(65536, first.getNextOffset());
        assertEquals(List.of("tail"),
                     NodeLogReader.read(log, first.getNextOffset(), 10).getLines());
    }
}
