package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.misc.LazyFileInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LazyFileInputStreamTest {
    @TempDir
    public File temporary;

    @Test
    public void opensOnlyWhenSendingAndSeeksForResume() throws Exception {
        Path path = temporary.toPath().resolve("queued.bin");
        try (LazyFileInputStream input = new LazyFileInputStream(path)) {
            Files.write(path, new byte[]{1, 2, 3, 4});
            input.mark(Integer.MAX_VALUE);
            assertEquals(3, input.skip(3));
            assertEquals(4, input.read());
            input.release();
            input.reset();
            assertEquals(1, input.read());
            input.mark(1);
            assertEquals(2, input.read());
            input.reset();
            assertEquals(2, input.read());
        }
        Files.delete(path);
    }

    @Test
    public void closingUnusedStreamDoesNotOpenTheFile() throws Exception {
        LazyFileInputStream input = new LazyFileInputStream(temporary.toPath().resolve("missing"));
        input.close();
        input.close();
        try {
            input.read();
            fail("Closed stream must not reopen");
        } catch (IOException expected) {
            assertEquals("Stream is closed", expected.getMessage());
        }
    }
}
