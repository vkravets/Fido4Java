package org.fidonet.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ReceivedFileArchiveTest {
    @TempDir
    Path root;

    private JFtnConfig config(boolean enabled) {
        return new JFtnConfig() {
            @Override
            public boolean isPreserveReceivedFiles() {
                return enabled;
            }

            @Override
            public Path getReceivedArchivePath() {
                return root.resolve("archive");
            }
        };
    }

    private List<Path> archived() throws IOException {
        try (Stream<Path> paths = Files.walk(root.resolve("archive"))) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    @Test
    void retainedOriginalsAndDifferentVersionsWithoutDuplicatingRetries() throws Exception {
        Path source = Files.writeString(root.resolve("source"), "first");
        ReceivedFileArchive.preserve(config(true), source, "mail.pkt");
        ReceivedFileArchive.preserve(config(true), source, "mail.pkt");
        assertEquals(1, archived().size());
        Files.writeString(source, "second");
        ReceivedFileArchive.preserve(config(true), source, "mail.pkt");
        assertEquals(2, archived().size());
        Files.delete(source);
        for (Path path : archived()) {
            assertEquals("mail.pkt", path.getFileName().toString());
            assertTrue(List.of("first", "second").contains(Files.readString(path)));
        }
    }

    @Test
    void disabledArchivingDidNotReadSourceOrCreateDirectories() throws Exception {
        ReceivedFileArchive.preserve(config(false), root.resolve("missing"), "mail.pkt");
        assertFalse(Files.exists(root.resolve("archive")));
    }

    @Test
    void failedArchivingKeptSourceAndRemovedStagingFiles() throws Exception {
        Path source = Files.writeString(root.resolve("source"), "retained");
        Files.writeString(root.resolve("archive"), "not a directory");
        assertThrows(IOException.class,
                     () -> ReceivedFileArchive.preserve(config(true), source, "mail.pkt"));
        assertEquals("retained", Files.readString(source));
        Files.delete(root.resolve("archive"));
        assertThrows(IOException.class,
                     () -> ReceivedFileArchive.preserve(config(true),
                                                        root.resolve("missing"),
                                                        "mail.pkt"));
        assertTrue(archived().isEmpty());
    }
}
