package org.fidonet.binkp.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.netty.plugin.codec.BinkDataDecoder;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class BinkDataDecoderTest {
    @Test
    public void decodesConsecutiveFramesFromFiveByteFragments() {
        assertFragmentedFrames(5);
    }

    @Test
    public void waitsForBothHeaderBytesAndTheEntirePayload() {
        assertFragmentedFrames(1);
    }

    private void assertFragmentedFrames(int fragmentSize) {
        byte[] payload = new byte[1025];
        new Random(19).nextBytes(payload);
        List<BinkFrame> frames = Arrays.asList(command("VER peer/1 binkp/1.1"),
                                               new BinkFrame((short) payload.length, payload),
                                               command("SYS Test station"));
        byte[] wire = wireBytes(frames);
        EmbeddedChannel channel = new EmbeddedChannel(new BinkDataDecoder());
        int frameIndex = 0;
        int nextBoundary = frames.get(0).getData().length + 2;
        try {
            for (int start = 0; start < wire.length; start += fragmentSize) {
                int end = Math.min(start + fragmentSize, wire.length);
                channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(wire, start, end)));
                while (frameIndex < frames.size() && nextBoundary <= end) {
                    BinkFrame actual = channel.readInbound();
                    assertNotNull(actual, "Complete frame must be emitted");
                    BinkFrame expected = frames.get(frameIndex);
                    assertEquals(expected.getDataInfo(), actual.getDataInfo());
                    assertArrayEquals(expected.getData(), actual.getData());
                    frameIndex++;
                    if (frameIndex < frames.size()) {
                        nextBoundary += frames.get(frameIndex).getData().length + 2;
                    }
                }
                assertNull(channel.readInbound(),
                           "No incomplete or duplicate frames may be emitted");
            }
            assertEquals(frames.size(), frameIndex);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private BinkFrame command(String text) {
        byte[] arguments = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[arguments.length + 1];
        System.arraycopy(arguments, 0, payload, 1, arguments.length);
        return new BinkFrame((short) (0x8000 | payload.length), payload);
    }

    private byte[] wireBytes(List<BinkFrame> frames) {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        for (BinkFrame frame : frames) {
            wire.write((frame.getDataInfo() >>> 8) & 0xff);
            wire.write(frame.getDataInfo() & 0xff);
            wire.write(frame.getData(), 0, frame.getData().length);
        }
        return wire.toByteArray();
    }
}
