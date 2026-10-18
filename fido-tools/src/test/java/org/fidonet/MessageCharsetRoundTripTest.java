package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

class MessageCharsetRoundTripTest {
    @Test
    void preservesHeadersAndBodyThroughPacketSerializationAndReexport() throws Exception {
        for (String[] fixture : new String[][]{{"LATIN-1 2", "café"}, {"LATIN-2 2", "Łódź"},
                {"LATIN-5 2", "İğdır"}, {"CP10000 2", "café"}, {"CP848 2", "ҐЄІЇ €"},
                {"IBMPC 2\r\1CODEPAGE: 1251", "Привіт"}}) {
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
