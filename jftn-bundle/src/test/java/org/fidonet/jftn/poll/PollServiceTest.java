package org.fidonet.jftn.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.binkp.netty.plugin.Server;
import org.fidonet.config.JFtnConfig;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class PollServiceTest {
    @Test
    void scheduledFailuresDidNotBlockOtherLinksAndEachAttemptOwnedItsContext() throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(("SysOp=Test\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                + "Link=1:500/101,1:500/100,secret,localhost\nLink=1:500/102,1:500/100,secret,localhost\n").getBytes(StandardCharsets.UTF_8)));
        SessionContext template = new SessionContext(new StationConfig("Test",
                                                                       "Sysop",
                                                                       "",
                                                                       "",
                                                                       "1:500/100"),
                                                     new LinksInfo(new ArrayList<>(config.getConfiguredLinks())));
        List<SessionContext> contexts = new CopyOnWriteArrayList<>();
        CountDownLatch entered = new CountDownLatch(2);
        try (PollService polls = new PollService(config, template, link -> new Client(link) {
            @Override
            public void run(SessionContext context) {
                contexts.add(context);
                context.getLinksInfo().setCurLink(link.getAddr());
                entered.countDown();
                if (link.getAddr().getNode() == 101) {
                    throw new IllegalStateException("Rejected");
                }
                context.setState(SessionState.STATE_END);
            }

            @Override
            public void stop() {
            }
        })) {
            polls.pollAll();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals("FAILED", polls.list().get(0).getState());
            assertTrue(contexts.get(0) != contexts.get(1));
            assertTrue(contexts.get(0) != template);
            assertTrue(contexts.get(0).getSendFiles() != contexts.get(1).getSendFiles());
            assertNull(template.getLinksInfo().getCurLink());
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void completesARealNettyPollAgainstAListeningBinkpPeer() throws Exception {
        Link caller = new Link("1:500/100,1:500/101,secret,localhost");
        SessionContext remoteContext = new SessionContext(new StationConfig("Remote",
                                                                            "Sysop",
                                                                            "",
                                                                            "",
                                                                            "1:500/101"),
                                                          new LinksInfo(List.of(caller)));
        Server remote = new Server(0);
        try {
            remote.run(remoteContext);
            JFtnConfig config = new JFtnConfig();
            String text = "SysOp=Test\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                          + "Link=1:500/101,1:500/100,secret,127.0.0.1:"
                          + remote.getLocalPort()
                          + "\n";
            config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            SessionContext local = new SessionContext(new StationConfig("Local",
                                                                        "Sysop",
                                                                        "",
                                                                        "",
                                                                        "1:500/100"),
                                                      new LinksInfo(new ArrayList<Link>(config.getConfiguredLinks())));
            try (PollService polls = new PollService(config, local)) {
                var job = polls.poll("1:500/101");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < deadline) {
                    job = polls.list().get(0);
                    if (job.getState().equals("SUCCEEDED") || job.getState().equals("FAILED")) {
                        break;
                    }
                    Thread.sleep(20);
                }
                assertEquals("SUCCEEDED", job.getState(), job.getDetail());
            }
        } finally {
            remote.stop();
        }
    }

    @Test
    void deduplicatesPeersCancelsQueuedAndRunningPollsAndClosesTheClient() throws Exception {
        String text = "SysOp=Test\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\n"
                      + "Link=1:500/102,1:500/100,secret,localhost\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        SessionContext template = new SessionContext(new StationConfig("Test",
                                                                       "Sysop",
                                                                       "",
                                                                       "",
                                                                       "1:500/100"),
                                                     new LinksInfo(new ArrayList<Link>(config.getConfiguredLinks())));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger clients = new AtomicInteger();
        try (PollService polls = new PollService(config, template, link -> new Client(link) {
            @Override
            public void run(SessionContext context) throws Exception {
                clients.incrementAndGet();
                entered.countDown();
                new CountDownLatch(1).await();
            }

            @Override
            public void stop() {
                stopped.countDown();
            }
        })) {
            var first = polls.poll("1:500/101");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(first.getId(), polls.poll("1:500/101").getId());
            polls.pollAll();
            assertEquals(2, polls.list().size());
            var second = polls.poll("1:500/102");
            polls.cancel(second.getId());
            polls.cancel(first.getId());
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
            assertTrue(polls.list().stream().allMatch(poll -> poll.getState().equals("CANCELLED")));
            assertEquals(1, clients.get());
            assertThrows(IllegalArgumentException.class, () -> polls.poll("1:500/999"));
        }
    }
}
