package org.fidonet.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.fts.FtsPkt;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

class AttachmentNamesTest {
    @Test
    void writesOnlyBasenamesForDosAndUnixAttachmentPaths() throws Exception {
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("1:500/100"),
                                      new FTNAddr("1:500/101"),
                                      "C:\\files\\ONE.ZIP, /srv/TWO.ZIP\tTHREE.ZIP",
                                      "Body\r",
                                      new Date());
        message.setAttributes(17);
        byte[] bytes = PacketWriter.write(message.getFAddr(),
                                          message.getTAddr(),
                                          "",
                                          List.of(message));
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(bytes));
        Message restored = new Message(packet.getMsgs()[0],
                                       packet.getOrigZone(),
                                       packet.getDestZone());
        assertEquals("ONE.ZIP TWO.ZIP THREE.ZIP", restored.getSubject());
        assertEquals("C:\\files\\ONE.ZIP, /srv/TWO.ZIP\tTHREE.ZIP", message.getSubject());
    }

    @Test
    void rejectsWildcardAndControlCharacterSubjects() {
        assertThrows(IOException.class, () -> AttachmentNames.wireSubject("*.ZIP"));
        assertThrows(IOException.class, () -> AttachmentNames.wireSubject("ONE.ZIP\rTWO.ZIP"));
        assertThrows(IOException.class, () -> AttachmentNames.wireSubject("../"));
    }
}
