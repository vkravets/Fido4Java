package org.fidonet.binkp.common.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileMetadataAndStorageTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void escapedFilenamesRoundTripThroughWireMetadata() throws Exception {
        String name = "file name\\with\tcontrols" + (char) 127 + ".pkt";
        FileInfo original = new FileInfo(name, 10, 123);
        FileInfo parsed = FileInfo.parseFileInfo(original.arguments() + " 4");
        assertEquals(original, parsed);
        assertEquals(original.hashCode(), parsed.hashCode());
        assertEquals(4, parsed.getOffset());
        assertEquals("a b", FileInfo.unescapeName("a\\20b"));
        assertEquals("x", FileInfo.unescapeName("\\78"));
        assertEquals(original, original);
        assertFalse(original.equals(null));
        assertFalse(original.equals(name));
        assertNotEquals(original, new FileInfo(name, 11, 123));
        assertNotEquals(original, new FileInfo(name, 10, 124));
        assertNotEquals(original, new FileInfo("other", 10, 123));
        assertEquals(0, FileInfo.parseFileInfo("file 0 123").getOffset());
    }

    @Test
    public void malformedMetadataAndEscapesAreRejected() throws Exception {
        for (String value : new String[]{"", "name 1", "name 1 2 3 4", "name -1 2", "name no 2",
                "name 1 no", "name 1 2 no", "\\x00 1 2", "\\ 1 2", "\\x1 1 2", "\\xzz 1 2"}) {
            try {
                FileInfo.parseFileInfo(value);
                fail("Accepted " + value);
            } catch (Exception expected) {
            }
        }
    }

    @Test
    public void partialOutputPersistsAndAppendsAcrossReopen() throws Exception {
        Path path = temporary.newFile().toPath();
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
        Path path = temporary.newFile().toPath();
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
