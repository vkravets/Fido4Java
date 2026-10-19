package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileEchoReplacementsTest {
    @TempDir
    Path directory;

    @Test
    void matchesCaseInsensitivelyAndRemovesOnlyActivePayloads() throws Exception {
        Files.writeString(directory.resolve("LIST.001"), "one");
        Files.writeString(directory.resolve("list.002"), "two");
        Files.writeString(directory.resolve("OTHER.ZIP"), "other");
        FileEchoReplacements.apply(directory, "list.???", "a".repeat(64));
        assertFalse(Files.exists(directory.resolve("LIST.001")));
        assertFalse(Files.exists(directory.resolve("list.002")));
        assertTrue(Files.exists(directory.resolve("OTHER.ZIP")));
        assertEquals("one",
                     Files.readString(directory.resolve("dupes/"
                                                        + "a".repeat(64)
                                                        + "/replaced/LIST.001")));
    }

    @Test
    void ignoresHtickLeadingStarAndRejectsTraversalBeforeChangingFiles() throws Exception {
        assertEquals("", FileEchoReplacements.mask("*.ZIP"));
        assertThrows(IOException.class, () -> FileEchoReplacements.mask("../LIST.*"));
        assertThrows(IOException.class, () -> FileEchoReplacements.mask("LIST\\?.ZIP"));
        assertThrows(IOException.class, () -> FileEchoReplacements.mask("C:LIST.*"));
        Files.writeString(directory.resolve("files.bbs"), "index");
        FileEchoReplacements.apply(directory, "files.*", "b".repeat(64));
        assertEquals("index", Files.readString(directory.resolve("files.bbs")));
    }
}
