package org.fidonet.binkp.mina2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.SessionTestFixtures;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.events.FileReceivedEventHandler;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.events.HasEventBus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Mina2TransportTest extends HasEventBus {
    @Test
    @Timeout(value = 20000, unit = TimeUnit.MILLISECONDS)
    public void productionClientAndServerExchangeAFileOnRandomTcpPort() throws Exception {
        SessionContext serverContext = context("1:500/100", "1:500/101", "secret", 0);
        Server server = new Server(0);
        Client client = null;
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<byte[]> contents = new AtomicReference<>();
        FileReceivedEventHandler receiver = new FileReceivedEventHandler() {
            @Override
            public void onEventHandle(FileReceivedEvent event) {
                contents.set(((ByteArrayOutputStream) event.getFile().getStream()).toByteArray());
                received.countDown();
            }
        };
        getEventBus().subscribe(receiver);
        try {
            server.run(serverContext);
            try {
                server.run(serverContext);
                fail("Duplicate start must not replace the listening acceptor");
            } catch (IllegalStateException expected) {
            }
            SessionContext outgoing = context("1:500/101",
                                              "1:500/100",
                                              "secret",
                                              server.getLocalPort());
            byte[] data = new byte[20003];
            new Random(17).nextBytes(data);
            outgoing.getSendFiles()
                    .add(new FileData<>(new FileInfo("packet.pkt", data.length, 123),
                                        new ByteArrayInputStream(data)));
            client = new Client(outgoing.getLinksInfo().getLinks().get(0), 3000);
            client.run(outgoing);
            assertTrue(client.isConnect());
            assertTrue(received.await(10, TimeUnit.SECONDS));
            assertArrayEquals(data, contents.get());
            awaitState(outgoing, SessionState.STATE_END);
            assertTrue(outgoing.isTransferComplete());
        } finally {
            if (client != null) {
                client.stop();
            }
            server.stop();
            server.stop();
            getEventBus().unsubscribe(receiver);
        }
    }

    @Test
    @Timeout(value = 20000, unit = TimeUnit.MILLISECONDS)
    public void invalidPasswordEndsSessionAndReleasesConnection() throws Exception {
        Server server = new Server(0);
        Client client = null;
        try {
            server.run(context("1:500/100", "1:500/101", "expected", 0));
            SessionContext outgoing = context("1:500/101",
                                              "1:500/100",
                                              "wrong",
                                              server.getLocalPort());
            client = new Client(outgoing.getLinksInfo().getLinks().get(0), 3000);
            client.run(outgoing);
            awaitState(outgoing, SessionState.STATE_ERR);
            assertFalse(outgoing.isTransferComplete());
        } finally {
            if (client != null) {
                client.stop();
            }
            server.stop();
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void stoppingAnUnstartedServerIsSafe() {
        Server server = new Server(0);
        server.stop();
        server.stop();
        try {
            server.getLocalPort();
            fail("Unstarted server has no listening port");
        } catch (IllegalStateException expected) {
        }
    }

    private static SessionContext context(String own, String peer, String password, int port) {
        return SessionTestFixtures.loopbackContext(own, peer, password, port);
    }

    private static void awaitState(SessionContext context, SessionState state) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (context.getState() != state && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(state, context.getState());
    }
}
