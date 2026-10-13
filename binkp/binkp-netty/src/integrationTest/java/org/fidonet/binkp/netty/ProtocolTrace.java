package org.fidonet.binkp.netty;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.FileInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.Assert.*;

/** Captures decoded commands in both directions without logging payload bytes or passwords. */
final class ProtocolTrace extends ChannelDuplexHandler {
    private final List<BinkFrame> incoming = new CopyOnWriteArrayList<>();
    private final List<BinkFrame> outgoing = new CopyOnWriteArrayList<>();
    private final List<String> lines = new CopyOnWriteArrayList<>();

    void assertExchange(boolean requestNr) throws Exception {
        assertTrue("JFtn must receive M_GOT", has(incoming, BinkCommand.M_GOT));
        assertTrue("JFtn must send M_GOT", has(outgoing, BinkCommand.M_GOT));
        assertTrue("binkd must send M_EOB", has(incoming, BinkCommand.M_EOB));
        assertTrue("JFtn must send M_EOB", has(outgoing, BinkCommand.M_EOB));
        assertEquals("binkd must honor JFtn's NR request", requestNr ? -1 : 0, firstIncomingFileOffset());
        assertEquals("JFtn must issue M_GET for NR reception", requestNr, has(outgoing, BinkCommand.M_GET));
    }

    String log() {
        return String.join("\n", lines);
    }

    @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        record("IN", incoming, message);
        super.channelRead(context, message);
    }

    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        record("OUT", outgoing, message);
        super.write(context, message, promise);
    }

    private void record(String direction, List<BinkFrame> target, Object message) {
        if (!(message instanceof BinkFrame)) return;
        BinkFrame frame = (BinkFrame) message;
        if (frame.getDataInfo() >= 0) return;
        target.add(new BinkFrame(frame.getDataInfo(), frame.getData().clone()));
        BinkCommand type = BinkCommand.findCommand(frame.getData()[0]);
        String command = type == BinkCommand.M_PWD ? "M_PWD [redacted]"
                : type + " " + new String(frame.getData(), 1, frame.getData().length - 1, StandardCharsets.UTF_8);
        lines.add(direction + " " + command);
    }

    private boolean has(List<BinkFrame> frames, BinkCommand command) {
        return frames.stream().anyMatch(frame -> frame.getData()[0] == command.getCmd());
    }

    private long firstIncomingFileOffset() throws Exception {
        for (BinkFrame frame : incoming) {
            if (frame.getData()[0] == BinkCommand.M_FILE.getCmd()) {
                return FileInfo.parseFileInfo(new String(frame.getData(), StandardCharsets.UTF_8).substring(1)).getOffset();
            }
        }
        throw new AssertionError("binkd did not send M_FILE");
    }
}
