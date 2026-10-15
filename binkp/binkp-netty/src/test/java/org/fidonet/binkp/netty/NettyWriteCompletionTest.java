package org.fidonet.binkp.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.netty.plugin.commons.NettySession;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class NettyWriteCompletionTest {
    @Test
    public void propagatesFailedTransportWrites() throws Exception {
        checkWrite(true, "Unable to write to the peer");
    }

    @Test
    public void rejectsBlockingTheEventLoop() throws Exception {
        checkWrite(false, "Cannot wait for file writes on the Netty event loop");
    }

    private void checkWrite(boolean failWrite, String expectedMessage) throws Exception {
        ChannelHandlerContext[] context = new ChannelHandlerContext[1];
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler() {
            @Override
            public void handlerAdded(ChannelHandlerContext added) {
                context[0] = added;
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                if (failWrite) {
                    promise.setFailure(new IOException("Disconnected"));
                }
            }
        });
        try {
            assertWriteRejected(new NettySession(context[0]), expectedMessage);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private void assertWriteRejected(NettySession session, String expectedMessage)
            throws Exception {
        session.write(new Object());
        try {
            session.awaitWrites();
            fail("Write must be rejected");
        } catch (IOException expected) {
            assertEquals(expectedMessage, expected.getMessage());
        }
    }
}
