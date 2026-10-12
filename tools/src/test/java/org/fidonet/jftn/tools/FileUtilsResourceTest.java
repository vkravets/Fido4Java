package org.fidonet.jftn.tools;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class FileUtilsResourceTest {
    @Test
    public void leavesCallerOwnedStreamOpenAfterReading() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        try (InputStream stream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() throws IOException { closed.set(true); super.close(); }
        }) {
            assertEquals("content", FileUtils.getFileContent(stream));
            assertFalse(closed.get());
        }
        assertTrue(closed.get());
    }

    @Test
    public void leavesCallerOwnedStreamOpenAfterReadFailure() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        try (InputStream stream = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failure"); }
            @Override public void close() { closed.set(true); }
        }) {
            try {
                FileUtils.getFileContent(stream);
                fail("Expected read failure");
            } catch (IOException expected) {
                assertFalse(closed.get());
            }
        }
        assertTrue(closed.get());
    }
}
