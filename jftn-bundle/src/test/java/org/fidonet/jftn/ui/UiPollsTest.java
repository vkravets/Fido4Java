package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class UiPollsTest {
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
            try (UiPolls polls = new UiPolls(config, local)) {
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
        try (UiPolls polls = new UiPolls(config, template, link -> new Client(link) {
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
