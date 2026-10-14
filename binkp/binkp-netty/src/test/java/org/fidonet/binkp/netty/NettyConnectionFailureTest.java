package org.fidonet.binkp.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.SessionTestFixtures;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.binkp.netty.plugin.Server;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class NettyConnectionFailureTest {
    private SessionContext context(String own, String peer, String password, int port) {
        return SessionTestFixtures.loopbackContext(own, peer, password, port);
    }

    @Test(timeout = 20000)
    public void busyServerRefusesSessionWithoutStartingTransfer() throws Exception {
        rejected(true);
    }

    @Test(timeout = 20000)
    public void wrongPasswordPreservesErrorWhenTcpCloses() throws Exception {
        rejected(false);
    }

    private void rejected(boolean busy) throws Exception {
        Server server = new Server(0);
        Client client = null;
        try {
            SessionContext template = context("1:500/100", "1:500/101", "secret", 0);
            template.setBusy(busy);
            server.run(template);
            SessionContext outgoing = context("1:500/101",
                                              "1:500/100",
                                              busy ? "secret" : "wrong",
                                              server.getLocalPort());
            client = new Client(outgoing.getLinksInfo().getLinks().get(0), 3000);
            client.run(outgoing);
            SessionState expected = busy ? SessionState.STATE_BSY : SessionState.STATE_ERR;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (outgoing.getState() != expected && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(expected, outgoing.getState());
            assertFalse(outgoing.isTransferComplete());
        } finally {
            if (client != null) {
                client.stop();
            }
            server.stop();
        }
    }

    @Test
    public void stopBeforeConnectIsSafeAndUnstartedServerHasNoPort() {
        new Client(null).stop();
        Server server = new Server(0);
        try {
            server.getLocalPort();
            fail("No listening port before bind");
        } catch (IllegalStateException expected) {
        } finally {
            server.stop();
        }
    }
}
