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
    void submissionRetriesReuseTheReservedMessageIdAfterReopen() throws Exception {
        Path path = root.resolve("ui");
        UserMailStateStore state = new UserMailStateStore(path);
        assertEquals("1:500/100 1234abcd",
                     state.reserve("request", "body", () -> "1:500/100 1234abcd"));
        UserMailStateStore reopened = new UserMailStateStore(path);
        assertEquals("1:500/100 1234abcd", reopened.reserve("request", "body", () -> {
            throw new AssertionError("A retry must not allocate another MSGID");
        }));
        assertThrows(IllegalArgumentException.class,
                     () -> reopened.reserve("request", "changed", () -> "1:500/100 1234abcf"));
    }

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

    @Test
    void originalReplyContextSurvivesReopenAndRemainsOwnerScoped() throws Exception {
        Path path = root.resolve("context");
        MailDraft draft = draft("reply").setOriginalBody("Original *plain text*")
                                        .setOriginalName("Peer")
                                        .setOriginalAddress("1:500/101")
                                        .setOriginalDate("20 Oct 26 10:00:00")
                                        .setComposeKind("quoted")
                                        .setTemplateInitialized(true)
                                        .setReplyTo("1:500/101 1234abcd");
        new MailDraftValidator(null).fields(draft);
        new UserMailStateStore(path).save("sysop", draft);
        UserMailStateStore reopened = new UserMailStateStore(path);
        assertEquals(draft, reopened.drafts("sysop").get(0));
        assertEquals(0, reopened.drafts("other").size());
    }

    @Test
    void originalTemplateContextHasBoundedSizeAndKnownCompositionKinds() {
        MailDraftValidator validator = new MailDraftValidator(null);
        assertThrows(IllegalArgumentException.class,
                     () -> validator.fields(draft("large").setOriginalBody("x".repeat(60001))));
        assertThrows(IllegalArgumentException.class,
                     () -> validator.fields(draft("date").setOriginalDate("x".repeat(101))));
        assertThrows(IllegalArgumentException.class,
                     () -> validator.fields(draft("kind").setComposeKind("execute")));
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
