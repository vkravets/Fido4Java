package org.fidonet.binkp.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.SessionTestFixtures;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.protocol.ProcessConnector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

class SshBinkdInteropTest {
    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(420)
    void exchangedFilesThroughOpenSshAndBinkdStdio(boolean nr) throws Exception {
        byte[] outgoing = payload(70003, 11);
        byte[] incoming = payload(45007, 12);
        Path artifacts = artifacts(nr ? "nr" : "normal");
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        try (SshBinkdTestPeer peer = new SshBinkdTestPeer(root.resolve("peer with spaces"))) {
            peer.start(incoming);
            SessionContext context = context(nr, log);
            AtomicReference<byte[]> received = new AtomicReference<>();
            context.setReceivedFileHandler((connection,
                                            file) -> received.set(((ByteArrayOutputStream) file.getStream()).toByteArray()));
            context.getSendFiles()
                   .add(new FileData<>(new FileInfo("LOCAL.PKT", outgoing.length, 123),
                                       new ByteArrayInputStream(outgoing)));
            ProcessConnector connector = new ProcessConnector(context.getLinksInfo()
                                                                     .getLinks()
                                                                     .get(0),
                                                              peer.command(),
                                                              Duration.ofSeconds(15));
            try {
                connector.run(context);
                awaitTerminal(context);
                connector.awaitCompletion();
                assertTrue(context.isTransferComplete(), context.getLastErrorMessage());
                assertArrayEquals(incoming, received.get());
                assertArrayEquals(outgoing, peer.received());
                assertEquals(nr,
                             log.stream()
                                .anyMatch(line -> line.contains("<<<")
                                        && line.contains("FILE REMOTE.PKT")
                                        && line.endsWith(" -1")));
                assertTrue(log.stream().anyMatch(line -> line.contains("PWD [redacted]")));
                assertTrue(log.stream().noneMatch(line -> line.contains("ssh-test-secret")));
            } finally {
                connector.stop();
                Files.write(artifacts.resolve("protocol.log"), log);
                peer.saveLogs(artifacts);
            }
        }
    }

    @Test
    @Timeout(420)
    void refusedUnknownHostKeyAndUnacceptedClientKey() throws Exception {
        try (SshBinkdTestPeer peer = new SshBinkdTestPeer(root.resolve("denied"))) {
            peer.start(new byte[]{1});
            peer.forgetHost();
            assertRejected(peer, "Host key verification failed");
            peer.trustHost();
            peer.denyAuthentication();
            assertRejected(peer, "Permission denied");
        }
    }

    private void assertRejected(SshBinkdTestPeer peer, String diagnostic) throws Exception {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        SessionContext context = context(false, log);
        ProcessConnector connector = new ProcessConnector(context.getLinksInfo().getLinks().get(0),
                                                          peer.command(),
                                                          Duration.ofSeconds(10));
        try {
            connector.run(context);
            awaitTerminal(context);
            assertEquals(SessionState.STATE_ERR, context.getState());
        } finally {
            connector.stop();
        }
        assertTrue(log.stream().anyMatch(line -> line.contains(diagnostic)),
                   String.join("\n", log));
        assertTrue(context.getRecvFiles().isEmpty());
    }

    private SessionContext context(boolean nr, List<String> log) {
        SessionContext context = SessionTestFixtures.loopbackContext("1:500/100",
                                                                     "1:500/101",
                                                                     "ssh-test-secret",
                                                                     0);
        context.getStationConfig().setNRRequested(nr);
        context.getLinksInfo().getLinks().get(0).setMD(true);
        context.setProtocolLogListener(log::add);
        return context;
    }

    private void awaitTerminal(SessionContext context) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (context.getState() != SessionState.STATE_END
                && context.getState() != SessionState.STATE_ERR && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(context.getState() == SessionState.STATE_END
                || context.getState() == SessionState.STATE_ERR);
    }

    private byte[] payload(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private Path artifacts(String mode) throws Exception {
        Path artifacts = Path.of(System.getProperty("binkd.artifactsDir"), "ssh", mode);
        Files.createDirectories(artifacts);
        return artifacts;
    }
}
