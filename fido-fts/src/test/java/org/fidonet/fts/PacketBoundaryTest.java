package org.fidonet.fts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class PacketBoundaryTest {
    private ByteBuffer packet(boolean extended) {
        ByteBuffer bytes = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort(0, (short) 65535);
        bytes.putShort(2, (short) 40000);
        bytes.putShort(18, (short) 2);
        bytes.putShort(20, (short) 50000);
        bytes.putShort(22, (short) 60000);
        bytes.putShort(34, (short) 40000);
        bytes.putShort(36, (short) 50000);
        if (extended) {
            bytes.putShort(40, (short) 256);
            bytes.putShort(44, (short) 1);
        }
        return bytes;
    }

    @Test
    public void decodesUnsignedAddressesAndFallsBackToLegacyZones() {
        for (boolean extended : new boolean[]{false, true}) {
            FtsPkt parsed = new FtsPkt(packet(extended));
            assertEquals(new FTNAddr(40000, 50000, 65535, 0), parsed.getOrigaddr());
            assertEquals(40000, parsed.getDestNode());
            assertEquals(60000, parsed.getDestNet());
            assertEquals(50000, parsed.getDestZone());
            assertEquals(40000, parsed.getOrigZone());
        }
        ByteBuffer explicit = packet(true);
        explicit.putShort(46, (short) 2);
        explicit.putShort(48, (short) 3);
        explicit.putShort(50, (short) 65535);
        explicit.putShort(52, (short) 40000);
        FtsPkt parsed = new FtsPkt(explicit);
        assertEquals(new FTNAddr(2, 50000, 65535, 65535), parsed.getOrigaddr());
        assertEquals(3, parsed.getDestZone());
        assertEquals(40000, parsed.getDestPoint());
    }

    @Test
    public void preservesHeaderMetadataAndReturnsIndependentArrayCopies() {
        ByteBuffer bytes = packet(false);
        for (int index = 0; index < 7; index++) {
            bytes.putShort(4 + 2 * index, (short) (index + 10));
        }
        bytes.put(24, (byte) 1);
        bytes.put(25, (byte) 2);
        bytes.put(42, (byte) 3);
        bytes.put(43, (byte) 4);
        bytes.putShort(38, (short) 5);
        bytes.position(26);
        bytes.put("12345678".getBytes(StandardCharsets.US_ASCII));
        bytes.put(54, (byte) 6);
        bytes.rewind();
        FtsPkt parsed = new FtsPkt(bytes);
        assertEquals(10, parsed.getYear());
        assertEquals(11, parsed.getMonth());
        assertEquals(12, parsed.getDay());
        assertEquals(13, parsed.getHour());
        assertEquals(14, parsed.getMin());
        assertEquals(15, parsed.getSec());
        assertEquals(16, parsed.getBoud());
        assertEquals(2, parsed.getType());
        assertEquals(5, parsed.getAuxNet());
        assertEquals(1, parsed.getProdCodeLow());
        assertEquals(2, parsed.getProdCodeMaj());
        assertEquals(3, parsed.getProdCodeH());
        assertEquals(4, parsed.getProdCodeMin());
        assertEquals("12345678", parsed.getPass());
        assertEquals(40000, parsed.getOrigZone0());
        assertEquals(50000, parsed.getDestZone0());
        assertEquals(0, parsed.getCapWord());
        assertEquals(0, parsed.getCapWordCopy());
        assertEquals(0, parsed.getOrigPoint());
        byte[] data = parsed.getAchData();
        data[0] = 0;
        assertEquals(6, parsed.getAchData()[0]);
        assertEquals(0, parsed.getMsgs().length);
    }

    @Test
    public void rejectsUnsupportedPacketTypeAndBytesAfterTerminator() {
        ByteBuffer invalid = packet(false);
        invalid.putShort(18, (short) 1);
        try {
            new FtsPkt(invalid);
            fail("Unsupported packet type accepted");
        } catch (IllegalArgumentException expected) {
        }
        ByteBuffer trailing = ByteBuffer.allocate(61).order(ByteOrder.LITTLE_ENDIAN);
        trailing.put(packet(false).array()).put((byte) 1).flip();
        try {
            new FtsPkt(trailing);
            fail("Trailing packet data accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new FtsPackMsg(ByteBuffer.allocate(33));
            fail("Truncated message accepted");
        } catch (IllegalArgumentException expected) {
        }
        ByteBuffer unknown = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
        unknown.putShort(0, (short) 3);
        assertFalse(new FtsPackMsg(unknown).isValid());
    }
}
