package org.fidonet.binkp.common.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.fidonet.binkp.common.RecordingSession;
import org.fidonet.binkp.common.SessionContext;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;

public class SenderBackpressureTest {
    @Test
    public void stopsReadingAtTheWriteWindowWhenTransportFails() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[1024 * 1024]);
        RecordingSession session = new RecordingSession() {
            @Override
            public void awaitWrites() throws IOException {
                throw new IOException("Peer disconnected");
            }
        };
        FilesSender<RecordingSession> sender = new FilesSender<>(session,
                                                                 new ArrayDeque<>(),
                                                                 new SessionContext(null, null));
        try {
            sender.send(new FileData<>(new FileInfo("large.bin", 1024 * 1024, 0), input));
            fail("Write failure must stop the sender");
        } catch (IOException expected) {
            assertEquals("Peer disconnected", expected.getMessage());
        }
        assertEquals(1024 * 1024 - 64 * 1024, input.available());
    }

    @Test
    public void drainsTheFinalShortWindow() throws Exception {
        int[] drains = {0};
        RecordingSession session = new RecordingSession() {
            @Override
            public void awaitWrites() {
                drains[0]++;
            }
        };
        FilesSender<RecordingSession> sender = new FilesSender<>(session,
                                                                 new ArrayDeque<>(),
                                                                 new SessionContext(null, null));
        sender.send(new FileData<>(new FileInfo("small.bin", 3, 0),
                                   new ByteArrayInputStream(new byte[3])));
        assertEquals(1, drains[0]);
    }
}
