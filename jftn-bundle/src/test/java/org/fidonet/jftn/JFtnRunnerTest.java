package org.fidonet.jftn;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.netty.plugin.Runner;
import org.fidonet.binkp.netty.plugin.NettyPlugin;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.Assert.*;

public class JFtnRunnerTest {
    @Test
    public void bundleDiscoversAndLoadsNettyTransport() throws Exception {
        Plugin<?> plugin = ServiceLoader.load(Plugin.class).stream()
                .filter(provider -> provider.type() == NettyPlugin.class)
                .findFirst().orElseThrow(() -> new AssertionError("Netty plugin missing from bundle"))
                .get();
        assertEquals(NettyPlugin.BINKP_PLUGIN_ID, plugin.getPluginInfo().getId());
        plugin.load();
        try {
            assertTrue(plugin.getContext().get() instanceof Runner);
        } finally {
            plugin.unload();
        }
        assertNull(plugin.getContext().get());
    }

    @Test
    public void pollingLinksKeepsSessionStateSeparateFromOtherLinksAndServer() {
        Link first = link("1:500/101");
        Link second = link("1:500/102");
        StationConfig config = new StationConfig("Test station", "Sysop", "Test location", "TCP,BINKP", "1:500/100");
        SessionContext server = new SessionContext(config,
                new LinksInfo(new ArrayList<>(Arrays.asList(first, second))));
        server.setState(SessionState.STATE_IDLE);
        List<SessionContext> sessions = new ArrayList<>();
        List<Link> polled = new ArrayList<>();
        Runner runner = new Runner() {
            @Override
            public void poll(Link link, SessionContext context) {
                polled.add(link);
                sessions.add(context);
                context.getLinksInfo().setCurLink(link.getAddr());
            }
        };
        try {
            JFtnRunner.poolLinks(server, runner);

            assertEquals(Arrays.asList(first, second), polled);
            assertEquals(2, sessions.size());
            SessionContext firstSession = sessions.get(0);
            SessionContext secondSession = sessions.get(1);
            assertNotSame(server, firstSession);
            assertNotSame(server, secondSession);
            assertNotSame(firstSession, secondSession);
            assertSame(config, firstSession.getStationConfig());
            assertSame(config, secondSession.getStationConfig());
            assertSame(first, firstSession.getLinksInfo().getCurLink());
            assertSame(second, secondSession.getLinksInfo().getCurLink());
            assertNull(server.getLinksInfo().getCurLink());
            assertNotSame(firstSession.getSendFiles(), secondSession.getSendFiles());
            assertNotSame(server.getSendFiles(), firstSession.getSendFiles());

            firstSession.setState(SessionState.STATE_END);
            firstSession.setSendingIsFinish(true);
            assertEquals(SessionState.STATE_IDLE, server.getState());
            assertNotEquals(SessionState.STATE_END, secondSession.getState());
            assertFalse(server.isSendingIsFinish());
            assertFalse(secondSession.isSendingIsFinish());
        } finally {
            runner.shutdown();
        }
    }

    private static Link link(String address) {
        return new Link(new FTNAddr(address), Collections.singletonList(new FTNAddr("1:500/100")),
                "secret", "127.0.0.1", 0);
    }
}
