package org.fidonet.binkp.common.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.binkp.common.RecordingSession;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionTestFixtures;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DurableCompletionTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void doesNotAcknowledgeWhenDurablePublicationFails() throws Exception {
        SessionContext context = SessionTestFixtures.context();
        FileInfo info = new FileInfo("mail.pkt", 3, 1);
        info.setCurSize(3);
        FileData<OutputStream> file = new FileData<>(info, new ByteArrayOutputStream());
        context.getRecvFiles().add(file);
        context.setReceivedFileHandler((session, received) -> {
            throw new IOException("Disk publication failed");
        });
        RecordingSession session = new RecordingSession();
        try {
            ReceiveTransfer.complete(session, context);
            fail("Failure must reach the transport");
        } catch (IOException expected) {
            assertEquals("Disk publication failed", expected.getMessage());
        }
        assertTrue(session.frames.isEmpty());
        assertFalse(info.isFinished());
        context.setReceivedFileHandler((current, received) -> assertTrue(session.frames.isEmpty()));
        ReceiveTransfer.complete(session, context);
        assertTrue(info.isFinished());
        assertEquals(1, session.frames.size());
    }

    @Test
    public void repeatedPublicationKeepsCompletedDataAndRefusesDifferentExistingContents()
            throws Exception {
        Path source = temporary.getRoot().toPath().resolve("mail.part");
        Path destination = temporary.getRoot().toPath().resolve("mail.pkt");
        PartialFileOutputStream stream = new PartialFileOutputStream(source, 3);
        stream.write(new byte[]{1, 2, 3});
        stream.close();
        stream.commitTo(destination);
        stream.commitTo(destination);
        assertArrayEquals(new byte[]{1, 2, 3}, stream.toByteArray());
        PartialFileOutputStream duplicate = new PartialFileOutputStream(source, 3);
        duplicate.write(new byte[]{1, 2, 3});
        duplicate.close();
        duplicate.commitTo(destination);
        assertFalse(Files.exists(source));
        PartialFileOutputStream conflict = new PartialFileOutputStream(source, 3);
        conflict.write(new byte[]{3, 2, 1});
        conflict.close();
        try {
            conflict.commitTo(destination);
            fail("Different contents must not overwrite a completed file");
        } catch (IOException expected) {
            assertTrue(Files.exists(source));
        }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(destination));
    }
}
