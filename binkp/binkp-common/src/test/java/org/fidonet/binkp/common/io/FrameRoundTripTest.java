package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.codec.DataReader;
import org.fidonet.binkp.common.commands.ERRCommand;
import org.fidonet.binkp.common.commands.share.BinkCommand;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class FrameRoundTripTest {
    @Test
    public void commandBuilderSupportsNullArgumentsAndDecoderIgnoresEmptyHeaders()
            throws Exception {
        BinkFrame frame = new ERRCommand().getData(null);
        assertEquals(0, BinkFrame.toBinkData(frame).getData().length);
        assertNull(DataReader.parseDataInfo(0));
        assertNull(DataReader.parseDataInfo(0x8000));
    }

    @Test
    public void preservesCommandArgumentsAndEmptyCommandPayload() {
        for (String arguments : new String[]{"", "packet.pkt 123 456 0", "station name"}) {
            byte[] bytes = arguments.getBytes(StandardCharsets.UTF_8);
            BinkData original = new BinkData(true, BinkCommand.M_FILE.getCmd(), bytes);
            BinkFrame frame = BinkData.toBinkFrame(original);
            assertEquals(bytes.length + 1, frame.getDataInfo() & 0x7fff);
            BinkData parsed = BinkFrame.toBinkData(frame);
            assertTrue(parsed.isCommand());
            assertEquals(original.getCommand(), parsed.getCommand());
            assertArrayEquals(bytes, parsed.getData());
        }
    }

    @Test
    public void preservesBinaryDataIncludingEmptyAndMaximumSizedFrames() {
        for (int length : new int[]{0, 1, 32767}) {
            byte[] bytes = new byte[length];
            new Random(123).nextBytes(bytes);
            BinkFrame frame = BinkData.toBinkFrame(new BinkData(false, (byte) -1, bytes));
            assertEquals(length, frame.getDataInfo());
            BinkData parsed = BinkFrame.toBinkData(frame);
            assertFalse(parsed.isCommand());
            assertArrayEquals(bytes, parsed.getData());
        }
        BinkData maximumCommand = new BinkData(true, (byte) 0, new byte[32766]);
        assertEquals(32766,
                     BinkFrame.toBinkData(BinkData.toBinkFrame(maximumCommand)).getData().length);
    }

    @Test
    public void rejectsMalformedFrameLengthsAndOversizedMessages() {
        for (BinkFrame invalid : new BinkFrame[]{new BinkFrame((short) 0x8000, new byte[0]),
                new BinkFrame((short) 3, new byte[2]), new BinkFrame((short) 1, null)}) {
            try {
                BinkFrame.toBinkData(invalid);
                fail("Malformed frame accepted");
            } catch (IllegalArgumentException expected) {
            }
        }
        for (boolean command : new boolean[]{false, true}) {
            try {
                BinkData.toBinkFrame(new BinkData(command, (byte) 0, new byte[32768]));
                fail("Oversized frame accepted");
            } catch (IllegalArgumentException expected) {
            }
        }
    }
}
