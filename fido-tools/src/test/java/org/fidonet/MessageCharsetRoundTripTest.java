package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

class MessageCharsetRoundTripTest {
    @Test
    void readsIndependentKoi8rPacketBytesAndPreservesThemOnExport() throws Exception {
        byte[] text = {(byte) 0xf0, (byte) 0xd2, (byte) 0xc9, (byte) 0xd7, (byte) 0xc5, (byte) 0xd4,
                0x20, (byte) 0xb3, (byte) 0xa3};
        ByteBuffer header = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
        for (int field : new int[]{2, 100, 101, 500, 500, 0, 0}) {
            header.putShort((short) field);
        }
        header.put("18 Oct 26  12:34:56\0".getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write(header.array());
        for (int field = 0; field < 3; field++) {
            input.write(text);
            input.write(0);
        }
        input.write("\1CHRS: KOI8-R 2\r".getBytes(StandardCharsets.US_ASCII));
        input.write(text);
        input.write('\r');
        input.write(0);
        Message message = new Message(new FtsPackMsg(ByteBuffer.wrap(input.toByteArray())), 1, 1);
        assertEquals("Привет Ёё", message.getFrom());
        assertEquals("Привет Ёё", message.getTo());
        assertEquals("Привет Ёё", message.getSubject());
        assertEquals("Привет Ёё\r", message.getBody().getBody());
        assertEquals("KOI8-R", message.getMessageCharset());
        byte[] exported = PacketWriter.write(new FTNAddr("1:500/100"),
                                             new FTNAddr("1:500/101"),
                                             "",
                                             List.of(message));
        // The first text field follows the 58-byte packet and 34-byte message headers.
        assertArrayEquals(text, Arrays.copyOfRange(exported, 92, 92 + text.length));
        Message restored = new Message(new FtsPkt(ByteBuffer.wrap(exported)).getMsgs()[0], 1, 1);
        assertEquals(message.getFrom(), restored.getFrom());
        assertEquals(message.getSubject(), restored.getSubject());
        assertEquals(message.getBody().getBody(), restored.getBody().getBody());
        assertEquals("KOI8-R", restored.getMessageCharset());
    }

    @Test
    void preservesHeadersAndBodyThroughPacketSerializationAndReexport() throws Exception {
        for (String[] fixture : new String[][]{{"LATIN-1 2", "café"}, {"LATIN-2 2", "Łódź"},
                {"LATIN-5 2", "İğdır"}, {"CP10000 2", "café"}, {"CP848 2", "ҐЄІЇ €"},
                {"IBMPC 2\r\1CODEPAGE: 1251", "Привіт"}, {"KOI8-R 2", "Привет Ёё"}}) {
            FTNAddr origin = new FTNAddr("1:500/100");
            FTNAddr destination = new FTNAddr("1:500/101");
            String text = fixture[1];
            Message message = new Message(text,
                                          text,
                                          origin,
                                          destination,
                                          text,
                                          "\1CHRS: " + fixture[0] + "\r" + text + "\r",
                                          new Date());
            for (int pass = 0; pass < 2; pass++) {
                byte[] bytes = PacketWriter.write(origin, destination, "", List.of(message));
                FtsPackMsg packed = new FtsPkt(ByteBuffer.wrap(bytes)).getMsgs()[0];
                message = new Message(packed, 1, 1);
                assertEquals(text, message.getFrom());
                assertEquals(text, message.getTo());
                assertEquals(text, message.getSubject());
                assertTrue(message.getBody().getBody().contains(text));
            }
        }
    }
}
