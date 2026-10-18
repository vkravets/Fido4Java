package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileMetadataAndStorageTest {
    @TempDir
    public File temporary;

    @Test
    public void escapedFilenamesRoundTripThroughWireMetadata() throws Exception {
        String name = "file name\\with\tcontrols" + (char) 127 + ".pkt";
        FileTransferInfo original = new FileTransferInfo(name, 10, 123);
        FileTransferInfo parsed = FileTransferInfo.parseFileInfo(original.arguments() + " 4");
        assertEquals(original, parsed);
        assertEquals(original.hashCode(), parsed.hashCode());
        assertEquals(4, parsed.getOffset());
        assertEquals("a b", FileTransferInfo.unescapeName("a\\20b"));
        assertEquals("x", FileTransferInfo.unescapeName("\\78"));
        assertEquals(original, original);
        assertFalse(original.equals(null));
        assertFalse(original.equals(name));
        assertNotEquals(original, new FileTransferInfo(name, 11, 123));
        assertNotEquals(original, new FileTransferInfo(name, 10, 124));
        assertNotEquals(original, new FileTransferInfo("other", 10, 123));
        assertEquals(0, FileTransferInfo.parseFileInfo("file 0 123").getOffset());
    }

    @Test
    public void malformedMetadataAndEscapesAreRejected() throws Exception {
        for (String value : new String[]{"", "name 1", "name 1 2 3 4", "name -1 2", "name no 2",
                "name 1 no", "name 1 2 no", "\\x00 1 2", "\\ 1 2", "\\x1 1 2", "\\xzz 1 2"}) {
            try {
                FileTransferInfo.parseFileInfo(value);
                fail("Accepted " + value);
            } catch (Exception expected) {
            }
        }
    }

    @Test
    public void partialOutputPersistsAndAppendsAcrossReopen() throws Exception {
        Path path = Files.createTempFile(temporary.toPath(), "test-", ".tmp");
        try (PartialFileOutputStream output = new PartialFileOutputStream(path, 10)) {
            output.write('a');
            output.write(new byte[]{'b', 'c'});
            output.write(new byte[]{0, 'd', 0}, 1, 1);
            assertEquals(4, output.length());
            assertEquals(4, output.size());
            assertEquals("abcd", output.toString(StandardCharsets.US_ASCII));
            assertEquals("abcd", output.toString("US-ASCII"));
            assertEquals("abcd", output.toString());
            ByteArrayOutputStream copy = new ByteArrayOutputStream();
            output.writeTo(copy);
            assertArrayEquals(new byte[]{'a', 'b', 'c', 'd'}, copy.toByteArray());
            try {
                output.reset();
                fail("Partial receive reset was allowed");
            } catch (UnsupportedOperationException expected) {
            }
            output.close();
            output.close();
            try {
                output.write('e');
                fail("Write after close succeeded");
            } catch (UncheckedIOException expected) {
            }
        }
        try (PartialFileOutputStream reopened = new PartialFileOutputStream(path, 10)) {
            reopened.write('e');
            assertEquals("abcde", reopened.toString(StandardCharsets.US_ASCII));
        }
    }

    @Test
    public void refusesOversizedOrAlreadyLockedPartialFilesAndReleasesFailedLocks()
            throws Exception {
        Path path = Files.createTempFile(temporary.toPath(), "test-", ".tmp");
        Files.write(path, new byte[3]);
        try {
            new PartialFileOutputStream(path, 2);
            fail("Oversized partial accepted");
        } catch (IOException expected) {
        }
        try (PartialFileOutputStream output = new PartialFileOutputStream(path, 3)) {
            try {
                new PartialFileOutputStream(path, 3);
                fail("Concurrent receive accepted");
            } catch (OverlappingFileLockException expected) {
            }
        }
        try (PartialFileOutputStream output = new PartialFileOutputStream(path, 3)) {
            assertEquals(3, output.length());
        }
    }
}
