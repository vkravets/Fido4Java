package org.fidonet.binkp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileTransferInfo;
import org.fidonet.binkp.common.protocol.ProcessConnector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class ProcessConnectorTest {
    @Test
    @Timeout(25)
    void exchangesWithARealChildProcessAndDrainsLargeStderrWithoutCorruptingFrames()
            throws Exception {
        SessionContext context = context();
        AtomicReference<String> payload = new AtomicReference<>();
        context.setReceivedFileHandler((connection,
                                        file) -> payload.set(((ByteArrayOutputStream) file.getStream()).toString(StandardCharsets.US_ASCII)));
        context.getSendFiles()
               .add(new FileData<>(new FileTransferInfo("LOCAL.PKT", 3, 123),
                                   new ByteArrayInputStream(new byte[]{1, 2, 3})));
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        context.setProtocolLogListener(log::add);
        ProcessConnector connector = connector(context, "server", Duration.ofSeconds(10));
        try {
            connector.run(context);
            awaitTerminal(context);
            connector.awaitCompletion();
            assertTrue(context.isTransferComplete(), context.getLastErrorMessage());
            assertEquals("Remote process payload", payload.get());
            assertTrue(log.stream().anyMatch(line -> line.contains("xxx")));
            assertTrue(log.stream().noneMatch(line -> line.contains("test-password")));
        } finally {
            connector.stop();
        }
    }

    @Test
    @Timeout(15)
    void reportsEarlyExitAndRedactsStderrCredentials() throws Exception {
        SessionContext context = context();
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        context.setProtocolLogListener(log::add);
        ProcessConnector connector = connector(context, "exit", Duration.ofSeconds(3));
        try {
            connector.run(context);
            awaitTerminal(context);
            assertEquals(SessionState.STATE_ERR, context.getState());
        } finally {
            connector.stop();
        }
        assertTrue(log.stream().anyMatch(line -> line.contains("Permission denied")));
        assertTrue(log.stream().noneMatch(line -> line.contains("test-password")));
    }

    @Test
    @Timeout(15)
    void idleProcessTimesOutAndCancellationBeforeStartPreventsLaunch() throws Exception {
        SessionContext context = context();
        ProcessConnector connector = connector(context, "idle", Duration.ofMillis(300));
        try {
            connector.run(context);
            awaitTerminal(context);
            assertEquals(SessionState.STATE_ERR, context.getState());
            assertTrue(context.getLastErrorMessage().contains("timed out"));
        } finally {
            connector.stop();
        }
        ProcessConnector cancelled = connector(context(), "idle", Duration.ofSeconds(3));
        cancelled.stop();
        assertThrows(Exception.class, () -> cancelled.run(context()));
    }

    private SessionContext context() {
        return SessionTestFixtures.loopbackContext("1:500/100", "1:500/101", "test-password", 0);
    }

    private ProcessConnector connector(SessionContext context, String mode, Duration timeout) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = List.of(java,
                                       "-Dlog4j2.configurationFile="
                                       + getClass().getResource("/process-log4j2.xml"),
                                       "-cp",
                                       System.getProperty("binkp.testClasspath"),
                                       ProcessTestPeer.class.getName(),
                                       mode);
        return new ProcessConnector(context.getLinksInfo().getLinks().get(0), command, timeout);
    }

    private void awaitTerminal(SessionContext context) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (context.getState() != SessionState.STATE_END
                && context.getState() != SessionState.STATE_ERR && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(context.getState() == SessionState.STATE_END
                || context.getState() == SessionState.STATE_ERR, "Child process did not finish");
    }
}
