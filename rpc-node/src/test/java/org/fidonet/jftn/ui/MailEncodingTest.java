package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.packet.PacketWriter;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.List;

class MailEncodingTest {
    @ParameterizedTest
    @CsvSource({"UTF-8, 4", "CP866, 2", "KOI8-R, 2", "CP1251, 2", "CP848, 2"})
    void submittedNetmailAndEchoUseDeclaredEncodingOnWire(String encoding, int level)
            throws Exception {
        for (Mailbox mailbox : List.of(Mailbox.OUTBOX, Mailbox.ECHO)) {
            MailDraft draft = draft(encoding);
            draft.setMailbox(mailbox);
            draft.setArea(mailbox == Mailbox.ECHO ? "TEST" : "");
            Message message = MailSubmissionService.createMessage(draft);
            assertEquals(encoding + " " + level, message.getBody().getKludges().get("CHRS").trim());
            byte[] bytes = PacketWriter.write(new FTNAddr("1:500/100"),
                                              new FTNAddr("1:500/101"),
                                              "",
                                              List.of(message));
            String wire = new String(bytes, CharsetTools.charsetDetect(encoding));
            assertTrue(wire.contains("Привет, мир!"));
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(bytes));
            Message restored = new Message(packet.getMsgs()[0],
                                           packet.getOrigZone(),
                                           packet.getDestZone());
            assertEquals(encoding, restored.getMessageCharset());
            assertTrue(restored.getBody().toString().contains("Привет, мир!"));
        }
    }

    @Test
    void rejectsUnknownEncodingAndUnrepresentableHeadersOrBody() {
        assertThrows(IllegalArgumentException.class, () -> MailEncoding.name(draft("unknown")));
        MailDraft body = draft("KOI8-R");
        body.setBody("Українська ї and emoji 🙂");
        assertThrows(IllegalArgumentException.class,
                     () -> MailSubmissionService.createMessage(body));
        MailDraft header = draft("CP866");
        header.setSubject("Emoji 🙂");
        assertThrows(IllegalArgumentException.class, () -> MailEncoding.validate(header));
        MailDraft utf = draft("UTF-8");
        utf.unsetEncoding();
        assertEquals("UTF-8 4", MailEncoding.declaration(utf));
    }

    private static MailDraft draft(String encoding) {
        return new MailDraft("draft",
                             Mailbox.OUTBOX,
                             "",
                             "Author",
                             "Reader",
                             "1:500/100.1",
                             "1:500/101",
                             "Тема",
                             "Привет, мир!").setEncoding(encoding);
    }
}
