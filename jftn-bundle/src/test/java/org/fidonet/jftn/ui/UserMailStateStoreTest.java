package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;

import org.apache.thrift.TSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

class UserMailStateStoreTest {
    @TempDir
    Path root;

    @Test
    void draftQuotaAllowsReplacingAnExistingDraftAndDoesNotAffectOtherOwners() throws Exception {
        Path path = root.resolve("ui");
        UserMailStateStore state = new UserMailStateStore(path);
        seedDrafts(path, "sysop", 200);
        state.save("sysop", draft("0").setSubject("updated"));
        assertEquals(200, state.drafts("sysop").size());
        assertEquals("updated", state.drafts("sysop").get(0).getSubject());
        assertThrows(IllegalArgumentException.class, () -> state.save("sysop", draft("new")));
        state.save("other", draft("new"));
        assertEquals(1, state.drafts("other").size());
    }

    private static void seedDrafts(Path path, String owner, int count) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:"
                                                                 + path.toAbsolutePath());
                PreparedStatement insert = connection.prepareStatement("INSERT INTO drafts(owner,id,payload) VALUES(?,?,?)")) {
            TSerializer serializer = new TSerializer();
            for (int i = 0; i < count; i++) {
                String id = Integer.toString(i);
                insert.setString(1, owner);
                insert.setString(2, id);
                insert.setBytes(3, serializer.serialize(draft(id)));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static MailDraft draft(String id) {
        return new MailDraft().setId(id)
                              .setMailbox(Mailbox.INBOX)
                              .setArea("")
                              .setFromName("Sysop")
                              .setToName("Peer")
                              .setFromAddress("1:500/100")
                              .setToAddress("1:500/101")
                              .setSubject("draft")
                              .setBody("body");
    }
}
