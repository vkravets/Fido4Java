package org.fidonet.jftn.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.config.JFtnConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

class PollHistoryTest {
    @TempDir
    Path root;

    private JFtnConfig config(String extra) throws Exception {
        JFtnConfig config = new JFtnConfig();
        String text = "SysOp=Test\nInbound=in\nOutbound=out\nTmp="
                      + root.toString()
                            .replace('\\', '/')
                      + "\nEchoPath=echo\nLink=1:500/101,1:500/100,secret,localhost\n"
                      + extra;
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private SessionContext template(JFtnConfig config) {
        return new SessionContext(new StationConfig("Test", "Sysop", "", "", "1:500/100"),
                                  new LinksInfo(new ArrayList<>(config.getConfiguredLinks())));
    }

    private PollService service(JFtnConfig config, Consumer<SessionContext> action) {
        return new PollService(config, template(config), link -> new Client(link) {
            @Override
            public void run(SessionContext context) {
                action.accept(context);
            }
            @Override
            public void stop() {
            }
        });
    }

    private PollAttempt finish(PollService service, String id) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (service.get(id).isActive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        PollAttempt result = service.get(id);
        assertTrue(!result.isActive(), result.getState());
        return result;
    }

    @Test
    void prematureCloseWasFailureAndBusyWasDistinctFromSuccess() throws Exception {
        JFtnConfig config = config("");
        try (PollService service = service(config,
                                           context -> context.setState(SessionState.STATE_END))) {
            PollAttempt result = finish(service, service.poll("1:500/101").getId());
            assertEquals("FAILED", result.getState());
            assertTrue(result.getStarted() > 0);
            assertTrue(result.getFinished() >= result.getStarted());
        }
        try (PollService service = service(config,
                                           context -> context.setState(SessionState.STATE_BSY))) {
            PollAttempt result = finish(service, service.poll("1:500/101").getId());
            assertEquals("BUSY", result.getState());
            assertEquals(2, service.list().size());
        }
    }

    @Test
    void restartRetainedHistoryAndInterruptedUnfinishedJobsWithoutRedialling() throws Exception {
        JFtnConfig config = config("");
        String id = UUID.randomUUID().toString();
        try (PollHistory history = new PollHistory(root.resolve("polls"))) {
            history.save(new PollAttempt(id, "1:500/101.0", "CONNECTING", 1, "").source("SCHEDULED")
                                                                                .started(2));
        }
        try (PollService service = service(config, context -> {
            throw new AssertionError("Must not redial");
        })) {
            PollAttempt record = service.get(id);
            assertEquals("INTERRUPTED", record.getState());
            assertEquals("SCHEDULED", record.getSource());
            assertEquals(2, record.getStarted());
            assertTrue(record.getFinished() > 0);
            assertThrows(RuntimeException.class, () -> service(config, context -> {
            }));
        }
        try (PollService service = service(config, context -> {
        })) {
            assertEquals("INTERRUPTED", service.get(id).getState());
        }
    }

    @Test
    void exceptionMessagesCausesAndStacksWereRetainedWithoutConfiguredCredentials()
            throws Exception {
        JFtnConfig config = config("");
        String id;
        try (PollService service = service(config, context -> {
            throw new IllegalStateException("Connection failed password=secret",
                                            new ConnectException("Connection refused on localhost:24554"));
        })) {
            id = service.poll("1:500/101").getId();
            PollAttempt result = finish(service, id);
            assertTrue(result.getDetail().contains("java.net.ConnectException"));
            assertTrue(result.getDetail().contains("Connection refused on localhost:24554"));
            assertFalse(result.getDetail().contains("secret"));
            String log = String.join("\n", service.logs(id, 0, 200).getLines());
            assertTrue(log.contains("Caused by: java.net.ConnectException"));
            assertTrue(log.contains("at org.fidonet.jftn.poll.PollHistoryTest"));
            assertFalse(log.contains("secret"));
        }
        try (PollService service = service(config, context -> {
        })) {
            assertTrue(service.get(id).getDetail().contains("Connection refused"));
            assertTrue(String.join("\n", service.logs(id, 0, 200).getLines())
                             .contains("Caused by:"));
        }
    }

    @Test
    void asynchronousFailuresAndRemoteErrorsKeptTheirActualReason() throws Exception {
        JFtnConfig config = config("");
        try (PollService service = service(config, context -> {
            new Thread(() -> context.fail(new IOException("Socket reset during transfer"))).start();
        })) {
            PollAttempt result = finish(service, service.poll("1:500/101").getId());
            assertTrue(result.getDetail()
                             .contains("java.io.IOException: Socket reset during transfer"));
        }
        try (PollService service = service(config, context -> {
            context.setLastErrorMessage("Remote refused authentication: password=secret");
            context.setState(SessionState.STATE_ERR);
        })) {
            PollAttempt result = finish(service, service.poll("1:500/101").getId());
            assertTrue(result.getDetail().contains("Remote refused authentication"));
            assertFalse(result.getDetail().contains("secret"));
        }
    }

    @Test
    void factoryFailuresBecameTerminalAndSessionsHadABoundedDeadline() throws Exception {
        JFtnConfig config = config("PollTimeoutSeconds=1\n");
        try (PollService service = new PollService(config, template(config), link -> {
            throw new IllegalStateException();
        })) {
            assertEquals("FAILED", finish(service, service.poll("1:500/101").getId()).getState());
        }
        try (PollService service = service(config, context -> {
        })) {
            assertEquals("TIMED_OUT",
                         finish(service, service.poll("1:500/101").getId()).getState());
        }
    }
}
