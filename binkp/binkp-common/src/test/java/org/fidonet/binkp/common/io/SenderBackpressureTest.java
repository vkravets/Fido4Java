package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.RecordingSession;
import org.fidonet.binkp.common.SessionContext;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        FileData<InputStream> file = new FileData<>(new FileInfo("small.bin", 3, 0),
                                                    new ByteArrayInputStream(new byte[3]));
        int[] releases = {0};
        file.setSendResource(() -> releases[0]++);
        sender.send(file);
        sender.send(file.resend(new FileInfo("small.bin", 3, 0)));
        assertEquals(2, releases[0]);
        assertEquals(2, drains[0]);
    }
}
