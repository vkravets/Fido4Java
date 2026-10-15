package org.fidonet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.fidonet.misc.AtomicFiles;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class AtomicFilesTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void publishesAndReplacesCompleteContents() throws Exception {
        Path target = temporary.getRoot().toPath().resolve("receipt");
        AtomicFiles.publish(target,
                            output -> output.write("first".getBytes(StandardCharsets.UTF_8)));
        assertEquals("first", Files.readString(target));
        AtomicFiles.replace(target,
                            output -> output.write("second".getBytes(StandardCharsets.UTF_8)));
        assertEquals("second", Files.readString(target));
        assertOnlyTargetRemains();
    }

    @Test
    public void retainsPreviousContentsAndCleansTemporaryFileWhenWriterFails() throws Exception {
        Path target = temporary.newFile("receipt").toPath();
        Files.writeString(target, "previous");
        try {
            AtomicFiles.replace(target, output -> {
                output.write("partial".getBytes(StandardCharsets.UTF_8));
                throw new IOException("write failed");
            });
            fail("Expected write failure");
        } catch (IOException failure) {
            assertEquals("write failed", failure.getMessage());
        }
        assertEquals("previous", Files.readString(target));
        assertOnlyTargetRemains();
    }

    private void assertOnlyTargetRemains() throws IOException {
        try (Stream<Path> files = Files.list(temporary.getRoot().toPath())) {
            assertEquals(1, files.count());
        }
    }
}
