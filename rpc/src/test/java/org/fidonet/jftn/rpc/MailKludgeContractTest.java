package org.fidonet.jftn.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

class MailKludgeContractTest {
    @Test
    void preservedOrderedRepeatedKludgesInMessagesAndDrafts() throws Exception {
        List<String> kludges = List.of("\u0001MSGID: 1:500/100 abc",
                                       "\u0001X-CUSTOM: first",
                                       "\u0001X-CUSTOM: second");
        MailItem message = new MailItem(1,
                                        "Author",
                                        "Reader",
                                        "1:500/100",
                                        "1:500/101",
                                        "Subject",
                                        "01 Jan 26  00:00:00",
                                        "1:500/100 abc",
                                        false);
        message.setBody("Message body");
        message.setKludges(kludges);
        MailItem restoredMessage = new MailItem();
        new TDeserializer().deserialize(restoredMessage, new TSerializer().serialize(message));
        assertEquals(kludges, restoredMessage.getKludges());
        assertEquals("Message body", restoredMessage.getBody());

        MailDraft draft = draft();
        draft.setOriginalKludges(kludges);
        MailDraft restoredDraft = new MailDraft();
        new TDeserializer().deserialize(restoredDraft, new TSerializer().serialize(draft));
        assertEquals(kludges, restoredDraft.getOriginalKludges());
        assertEquals(draft.getBody(), restoredDraft.getBody());
    }

    @Test
    void loadedDraftPayloadWithoutOriginalKludges() throws Exception {
        // An unset optional field is omitted, matching the pre-kludge draft wire format.
        MailDraft original = draft();
        MailDraft restored = new MailDraft();
        new TDeserializer().deserialize(restored, new TSerializer().serialize(original));
        assertEquals(original, restored);
        assertFalse(restored.isSetOriginalKludges());
        assertEquals(14, MailItem._Fields.KLUDGES.getThriftFieldId());
        assertEquals(18, MailDraft._Fields.ORIGINAL_KLUDGES.getThriftFieldId());
    }

    private static MailDraft draft() {
        return new MailDraft("draft",
                             Mailbox.OUTBOX,
                             "",
                             "Author",
                             "Reader",
                             "1:500/100",
                             "1:500/101",
                             "Subject",
                             "Draft body");
    }
}
