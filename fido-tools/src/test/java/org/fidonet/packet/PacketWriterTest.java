package org.fidonet.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.fts.FtsPkt;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

public class PacketWriterTest {
    @Test
    public void writesType2PlusEnvelopeAndIndependentFourDimensionalMessageAddresses()
            throws Exception {
        Message original = new Message("Автор",
                                       "Получатель",
                                       new FTNAddr("1:100/200.40000"),
                                       new FTNAddr("3:300/400.50000"),
                                       "Тема",
                                       "\1CHRS: UTF-8 4\rТекст\r",
                                       new Date(0));
        original.setAttributes(0xffff);
        byte[] bytes = PacketWriter.write(new FTNAddr("2:500/102.60000"),
                                          new FTNAddr("2:500/101"),
                                          "secret",
                                          Collections.singletonList(original));
        ByteBuffer raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2, raw.getShort(18));
        assertEquals(1, raw.getShort(44));
        assertEquals(0x0100, raw.getShort(40));
        assertEquals(60000, Short.toUnsignedInt(raw.getShort(50)));
        assertEquals(0x7013, Short.toUnsignedInt(raw.getShort(68)));
        assertEquals(0, raw.getShort(bytes.length - 2));
        String body = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(body.contains("\1INTL 3:300/400 1:100/200\r"));
        assertTrue(body.contains("\1FMPT 40000\r"));
        assertTrue(body.contains("\1TOPT 50000\r"));
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(bytes));
        Message restored = new Message(packet.getMsgs()[0],
                                       packet.getOrigZone(),
                                       packet.getDestZone());
        assertEquals(original.getFAddr(), restored.getFAddr());
        assertEquals(original.getTAddr(), restored.getTAddr());
        assertEquals(original.getFrom(), restored.getFrom());
        assertEquals(original.getMsgDate(), restored.getMsgDate());
    }

    @Test
    public void enforcesEncodedByteLimitsInsteadOfCharacterCounts() throws Exception {
        Message message = new Message("я".repeat(18),
                                      "Reader",
                                      new FTNAddr("2:500/102"),
                                      new FTNAddr("2:500/101"),
                                      "Subject",
                                      "\1CHRS: UTF-8 4\rBody\r",
                                      new Date());
        assertThrows(IOException.class,
                     () -> PacketWriter.write(message.getFAddr(),
                                              message.getTAddr(),
                                              "",
                                              Collections.singletonList(message)));
    }
}
