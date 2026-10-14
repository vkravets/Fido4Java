package org.fidonet.binkp.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.io.BinkData;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.netty.plugin.handler.BinkSessionHandler;
import org.fidonet.events.HasEventBus;
import org.fidonet.types.Link;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public class HandlerFailureTest extends HasEventBus {
    private SessionContext context() {
        Link link = new Link("2:500/2, 2:500/1, secret, localhost");
        SessionContext context = new SessionContext(new StationConfig("station",
                                                                      "sysop",
                                                                      "city",
                                                                      "BINKP",
                                                                      "2:500/1"),
                                                    new LinksInfo(new ArrayList<>(Collections.singletonList(link))));
        context.getLinksInfo().setCurLink(link.getAddr());
        context.setServerRole(ServerRole.CLIENT);
        return context;
    }

    @Test
    public void malformedCommandClosesChannelAndKeepsErrorStateAndMessage() {
        SessionContext context = context();
        EmbeddedChannel channel = new EmbeddedChannel(new BinkSessionHandler(context,
                                                                             getEventBus()));
        try {
            channel.writeInbound(new BinkFrame((short) 0x8002, new byte[]{1}));
            assertEquals(SessionState.STATE_ERR, context.getState());
            assertFalse(channel.isOpen());
            assertTrue(context.getLastErrorMessage().contains("length"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void invalidFileOfferClosesChannelAndPreservesDiagnostic() {
        SessionContext context = context();
        EmbeddedChannel channel = new EmbeddedChannel(new BinkSessionHandler(context,
                                                                             getEventBus()));
        try {
            channel.writeInbound(BinkData.toBinkFrame(new BinkData(true,
                                                                   (byte) 3,
                                                                   "file 1 123 2".getBytes(StandardCharsets.UTF_8))));
            assertEquals(SessionState.STATE_ERR, context.getState());
            assertEquals("Invalid file offset", context.getLastErrorMessage());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void normalCloseEndsSessionAndBusyEmbeddedServerOnlySendsRefusal() {
        SessionContext context = context();
        EmbeddedChannel normal = new EmbeddedChannel(new BinkSessionHandler(context,
                                                                            getEventBus()));
        normal.finishAndReleaseAll();
        assertEquals(SessionState.STATE_END, context.getState());
        SessionContext busy = context();
        busy.setServerRole(ServerRole.SERVER);
        busy.setBusy(true);
        EmbeddedChannel channel = new EmbeddedChannel(new BinkSessionHandler(busy, getEventBus()));
        try {
            assertEquals(SessionState.STATE_BSY, busy.getState());
            BinkFrame refusal = channel.readOutbound();
            assertEquals((byte) 8, BinkFrame.toBinkData(refusal).getCommand());
            Object flushed = channel.readOutbound();
            if (flushed instanceof ByteBuf) {
                assertEquals(0, ((ByteBuf) flushed).readableBytes());
                ReferenceCountUtil.release(flushed);
            }
            assertNull(channel.readOutbound());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
