package org.fidonet.binkp.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileTransferInfo;
import org.fidonet.binkp.common.protocol.StreamSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class StreamSessionTest {
    @Test
    @Timeout(30)
    void fragmentedDuplexStreamsExchangeFilesWithNrAndEncryptionAcrossBatches() throws Exception {
        for (boolean crypt : new boolean[]{false, true}) {
            exchange(crypt, true);
        }
        exchange(false, false);
    }

    private void exchange(boolean crypt, boolean version11) throws Exception {
        SessionContext client = context(ServerRole.CLIENT, crypt, version11);
        SessionContext server = context(ServerRole.SERVER, crypt, version11);
        byte[] payload = new byte[70001];
        new Random(17).nextBytes(payload);
        ConcurrentHashMap<String, byte[]> received = new ConcurrentHashMap<>();
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        configureFiles(client, payload, received, "client", version11);
        configureFiles(server, payload, received, "server", version11);
        client.setProtocolLogListener(log::add);
        try (PipedInputStream clientInput = new PipedInputStream(131072);
                PipedInputStream serverInput = new PipedInputStream(131072);
                StreamSession originator = stream(clientInput,
                                                  new PipedOutputStream(serverInput),
                                                  client);
                StreamSession answerer = stream(serverInput,
                                                new PipedOutputStream(clientInput),
                                                server)) {
            answerer.start();
            originator.start();
            assertTrue(originator.awaitClosed(Duration.ofSeconds(10)),
                       client.getLastErrorMessage());
            assertTrue(answerer.awaitClosed(Duration.ofSeconds(10)), server.getLastErrorMessage());
            assertEquals(SessionState.STATE_END, client.getState(), client.getLastErrorMessage());
            assertEquals(SessionState.STATE_END, server.getState(), server.getLastErrorMessage());
            assertEquals(version11 ? 4 : 2, received.size());
            for (byte[] actual : received.values()) {
                assertArrayEquals(payload, actual);
            }
            assertTrue(log.stream()
                          .anyMatch(line -> line.contains("<<<") && line.contains("FILE")));
            assertTrue(log.stream().anyMatch(line -> line.contains("PWD [redacted]")));
            assertTrue(log.stream().noneMatch(line -> line.contains("test-password")));
            assertEquals(crypt, client.isCryptMode());
        }
    }

    private void configureFiles(SessionContext context,
                                byte[] payload,
                                ConcurrentHashMap<String, byte[]> received,
                                String name,
                                boolean batches) {
        AtomicInteger loaded = new AtomicInteger();
        context.setOutboundLoader(connection -> {
            int batch = loaded.getAndIncrement();
            if (batch == 0 || batches && batch == 1) {
                connection.getSendFiles()
                          .add(new FileData<>(new FileTransferInfo(name + batch + ".pkt",
                                                                   payload.length,
                                                                   123),
                                              new ByteArrayInputStream(payload)));
            }
        });
        context.setReceivedFileHandler((connection,
                                        file) -> received.put(file.getInfo().getName(),
                                                              ((ByteArrayOutputStream) file.getStream()).toByteArray()));
    }

    private SessionContext context(ServerRole role, boolean crypt, boolean version11) {
        String own = role == ServerRole.CLIENT ? "1:500/100" : "1:500/101";
        String peer = role == ServerRole.CLIENT ? "1:500/101" : "1:500/100";
        SessionContext context = SessionTestFixtures.loopbackContext(own, peer, "test-password", 0);
        context.setServerRole(role);
        context.getStationConfig().setCryptMode(crypt);
        context.getStationConfig().setBinkp11Enabled(version11);
        context.getStationConfig().setNRRequested(version11);
        context.getLinksInfo().getLinks().get(0).setMD(true);
        if (role == ServerRole.CLIENT) {
            context.getLinksInfo().setCurLink(context.getLinksInfo().getLinks().get(0).getAddr());
        }
        return context;
    }

    private StreamSession stream(InputStream input,
                                 PipedOutputStream output,
                                 SessionContext context) {
        InputStream fragments = new FilterInputStream(input) {
            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                return super.read(bytes, offset, Math.min(3, length));
            }
        };
        return new StreamSession(fragments, output, context, "test", Duration.ofSeconds(5), () -> {
        });
    }

    @Test
    void rejectsEofInAHeaderOrPayloadAndTransferBeforeAuthentication() throws Exception {
        for (byte[] bytes : new byte[][]{{(byte) 0x80}, {(byte) 0x80, 3, 0},
                {(byte) 0x80, 2, 3, 'x'}}) {
            SessionContext context = context(ServerRole.SERVER, false, false);
            try (StreamSession session = new StreamSession(new ByteArrayInputStream(bytes),
                                                           new ByteArrayOutputStream(),
                                                           context,
                                                           "test",
                                                           Duration.ofSeconds(2),
                                                           () -> {
                                                           })) {
                session.start();
                assertTrue(session.awaitClosed(Duration.ofSeconds(3)));
                assertEquals(SessionState.STATE_ERR, context.getState());
                assertTrue(context.getRecvFiles().isEmpty());
            }
        }
    }
}
