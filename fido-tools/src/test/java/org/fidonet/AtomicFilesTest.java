package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.misc.AtomicFiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class AtomicFilesTest {
    @TempDir
    public File temporary;

    @Test
    public void publishesAndReplacesCompleteContents() throws Exception {
        Path target = temporary.toPath().resolve("receipt");
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
        Path target = Files.createFile(temporary.toPath().resolve("receipt"));
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
        try (Stream<Path> files = Files.list(temporary.toPath())) {
            assertEquals(1, files.count());
        }
    }
}
