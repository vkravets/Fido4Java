package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.ui.NodeRpc;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class EchoReaderRpcTest {
    @TempDir
    Path root;
    private MailDatabaseFacade base;
    private MailRuntime mail;
    private NodeRpc rpc;
    private SessionContext context;
    private THttpClient connection;
    private UiService.Client ui;
    private String admin;

    @BeforeEach
    void open() throws Exception {
        Path source = root.resolve("jftn.conf");
        Files.writeString(source,
                          "SysOp=Test\nInbound=" + root.resolve("in") + "\nOutbound="
                                  + root.resolve("out") + "\nTmp=" + root.resolve("tmp")
                                  + "\nEchoPath=" + root.resolve("echo")
                                  + "\nLink=1:500/101,1:500/100,secret,localhost\n"
                                  + "Link=1:500/100.1,1:500/100,secret,localhost\n"
                                  + "Link=1:500/100.2,1:500/100,secret,localhost\n"
                                  + "JamBridgeEnabled=false\nEchoSubscriptions=database\n"
                                  + "RpcEnabled=true\nRpcPort=0\nRpcUser=sysop\nRpcPassword=ui-secret\n"
                                  + "RpcHttpEnabled=true\nRpcHttpPort=0\nRpcHttpOrigin=http://localhost:5173\n");
        JFtnConfig config = new JFtnConfig();
        config.load(source.toString());
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:echoReader" + System.nanoTime()));
        assertTrue(base.open());
        base.createArea("test");
        base.createArea("private");
        mail = new MailRuntime(config, base);
        context = NodeSessions.create(config);
        rpc = NodeRpc.open(config, base, mail, context);
        connection = endpoint("ui");
        ui = new UiService.Client(new TJSONProtocol(connection));
        admin = login("sysop", "ui-secret");
        ui.subscribe(admin, "1:500/101", "test", "BOTH");
    }

    private THttpClient endpoint(String service) throws Exception {
        return new THttpClient("http://127.0.0.1:" + rpc.getHttpPort() + "/rpc/" + service);
    }

    private String login(String name, String password) throws Exception {
        try (THttpClient client = endpoint("login")) {
            return new LoginService.Client(new TJSONProtocol(client)).login(new Credential(name,
                                                                                           password))
                                                                     .getSession_id();
        }
    }

    private String point() throws Exception {
        try (THttpClient client = endpoint("profile")) {
            ProfileService.Client profiles = new ProfileService.Client(new TJSONProtocol(client));
            profiles.register("point", "point-password-123", "1:500/100.1");
            profiles.decideAka(admin, "point", "1:500/100.1", true);
        }
        return login("point", "point-password-123");
    }

    private MailDraft draft() {
        return new MailDraft("echo-draft",
                             Mailbox.ECHO,
                             "test",
                             "Point",
                             "All",
                             "1:500/100.1",
                             "1:500/100",
                             "Discussion",
                             "Hello from an approved point");
    }

    private void restartSearch(String value) throws Exception {
        connection.close();
        rpc.close();
        Path fragments = root.resolve("conf.d");
        Files.createDirectories(fragments);
        Files.writeString(fragments.resolve("90-rpc.conf"),
                          "RpcExperimentalMailSearch=" + value + "\n");
        JFtnConfig config = new JFtnConfig();
        config.load(root.resolve("jftn.conf").toString());
        rpc = NodeRpc.open(config, base, mail, NodeSessions.create(config));
        connection = endpoint("ui");
        ui = new UiService.Client(new TJSONProtocol(connection));
        admin = login("sysop", "ui-secret");
    }

    @Test
    void searchIsDisabledByDefaultAndItsFileOnlySwitchRequiresRestart() throws Exception {
        assertEquals("FEATURE_DISABLED",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages(admin,
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "body",
                                                          0,
                                                          10)).getCode());
        assertEquals("UNAUTHORIZED",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages("invalid",
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "body",
                                                          0,
                                                          10)).getCode());
        restartSearch("true");
        assertFalse(ui.searchMessages(admin, Mailbox.ECHO, "test", "body", 0, 10).isHasMore());
        Files.writeString(root.resolve("conf.d/90-rpc.conf"), "RpcExperimentalMailSearch=false\n");
        try (THttpClient client = endpoint("settings")) {
            SettingsService.Client settings = new SettingsService.Client(new TJSONProtocol(client));
            UiError failure = assertThrows(UiError.class, () -> settings.reload(admin));
            assertEquals("INVALID_ARGUMENT", failure.getCode());
            assertTrue(failure.getMessage().toLowerCase().contains("rpcexperimentalmailsearch"));
        }
        assertTrue(ui.searchMessages(admin, Mailbox.ECHO, "test", "body", 0, 10)
                     .getItems()
                     .isEmpty());
        restartSearch("false");
        assertEquals("FEATURE_DISABLED",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages(admin,
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "body",
                                                          0,
                                                          10)).getCode());
        assertThrows(IllegalArgumentException.class, () -> restartSearch("maybe"));
    }

    @Test
    void searchMatchesUnicodeBodyBeyondExcerptsAndTreatsQueryAsLiteralText() throws Exception {
        restartSearch("true");
        storeSearchText("test",
                        "unicode",
                        "context ".repeat(70)
                        + "\u041f\u0420\u0418\u0412\u0406\u0422 \u0421\u0412\u0406\u0422\u0415",
                        "Topic");
        storeSearchText("test", "literal", "Literal [.*]%_ text", "Other");
        storeSearchText("test", "hidden", "\1SEARCH: hidden-token\rVisible text", "Other");
        var page = ui.searchMessages(admin,
                                     Mailbox.ECHO,
                                     "TEST",
                                     "\u043f\u0440\u0438\u0432\u0456\u0442",
                                     0,
                                     10);
        assertEquals(1, page.getItemsSize());
        var found = page.getItems().get(0);
        assertTrue(found.getExcerpt().contains("\u041f\u0420\u0418\u0412\u0406\u0422"));
        assertFalse(found.isSetBody());
        assertFalse(found.isRead());
        assertFalse(ui.message(admin, Mailbox.ECHO, "test", found.getId()).isRead());
        assertEquals(1,
                     ui.searchMessages(admin, Mailbox.ECHO, "test", "[.*]%_", 0, 10)
                       .getItemsSize());
        assertTrue(ui.searchMessages(admin, Mailbox.ECHO, "test", "hidden-token", 0, 10)
                     .getItems()
                     .isEmpty());
        assertEquals(3,
                     ui.searchMessages(admin, Mailbox.ECHO, "test", "AUTHOR", 0, 10)
                       .getItemsSize());
        assertEquals(3,
                     ui.searchMessages(admin, Mailbox.ECHO, "test", "1:500/101", 0, 10)
                       .getItemsSize());
        assertEquals(1,
                     ui.searchMessages(admin, Mailbox.ECHO, "test", "topic", 0, 10).getItemsSize());
        ui.markRead(admin, Mailbox.ECHO, "test", found.getId(), true);
        assertTrue(ui.searchMessages(admin,
                                     Mailbox.ECHO,
                                     "test",
                                     "\u043f\u0440\u0438\u0432\u0456\u0442",
                                     0,
                                     10)
                     .getItems()
                     .get(0)
                     .isRead());
        for (String query : List.of("", " ", "x", "x".repeat(161), "bad\nquery", "bad\0query")) {
            assertEquals("INVALID_ARGUMENT",
                         assertThrows(UiError.class,
                                      () -> ui.searchMessages(admin,
                                                              Mailbox.ECHO,
                                                              "test",
                                                              query,
                                                              0,
                                                              10)).getCode());
        }
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages(admin,
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "body",
                                                          -1,
                                                          10)).getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages(admin,
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "body",
                                                          0,
                                                          101)).getCode());
    }

    @Test
    void searchPagesRespectScanBudgetAndContinueAfterEmptyPagesWithoutSkippingMatches()
            throws Exception {
        restartSearch("true");
        for (int index = 0; index < 1000; index++) {
            storeSearchText("test", "old-" + index, "Unrelated body", "Archive");
        }
        storeSearchText("test", "last-1", "Needle one", "Archive");
        storeSearchText("test", "last-2", "Needle two", "Archive");
        var empty = ui.searchMessages(admin, Mailbox.ECHO, "test", "needle", 0, 1);
        assertEquals(1000, empty.getScanned());
        assertEquals(0, empty.getItemsSize());
        assertTrue(empty.isHasMore());
        assertTrue(empty.getNextId() > 0);
        var first = ui.searchMessages(admin, Mailbox.ECHO, "test", "needle", empty.getNextId(), 1);
        assertEquals(1, first.getItemsSize());
        assertEquals(1, first.getScanned());
        assertTrue(first.isHasMore());
        var last = ui.searchMessages(admin, Mailbox.ECHO, "test", "needle", first.getNextId(), 1);
        assertEquals(1, last.getItemsSize());
        assertFalse(last.isHasMore());
        assertTrue(last.getItems().get(0).getId() > first.getItems().get(0).getId());
    }

    @Test
    void searchEnforcesLiveAreaReadRightsOnHttpAndTcp() throws Exception {
        restartSearch("true");
        String point = point();
        storeSearchText("test", "public", "Needle public", "Topic");
        storeSearchText("private", "private", "Needle private", "Topic");
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages(point,
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "needle",
                                                          0,
                                                          10)).getCode());
        ui.subscribe(admin, "1:500/100.1", "test", "WRITE");
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.searchMessages(point,
                                                          Mailbox.ECHO,
                                                          "test",
                                                          "needle",
                                                          0,
                                                          10)).getCode());
        ui.subscribe(admin, "1:500/100.1", "test", "READ");
        try (TFramedTransport transport = new TFramedTransport(new TSocket("127.0.0.1",
                                                                           rpc.getPort(),
                                                                           5000))) {
            transport.open();
            UiService.Client tcp = new UiService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                 "ui"));
            assertEquals(1,
                         tcp.searchMessages(point, Mailbox.ECHO, "test", "needle", 0, 10)
                            .getItemsSize());
            assertEquals("FORBIDDEN",
                         assertThrows(UiError.class,
                                      () -> tcp.searchMessages(point,
                                                               Mailbox.ECHO,
                                                               "private",
                                                               "needle",
                                                               0,
                                                               10)).getCode());
            ui.unsubscribe(admin, "1:500/100.1", "test");
            assertEquals("FORBIDDEN",
                         assertThrows(UiError.class,
                                      () -> tcp.searchMessages(point,
                                                               Mailbox.ECHO,
                                                               "test",
                                                               "needle",
                                                               0,
                                                               10)).getCode());
        }
    }

    @Test
    void searchScopesNetmailToOwnedAkasAndHonorsWaitingAndDeletedState() throws Exception {
        restartSearch("true");
        String point = point();
        MailDraft direct = draft().setMailbox(Mailbox.OUTBOX)
                                  .setArea("")
                                  .setFromAddress("1:500/100")
                                  .setToAddress("1:500/100.1")
                                  .setBody("Needle private netmail");
        ui.send(admin, direct, "search-point");
        ui.send(admin,
                direct.deepCopy().setId("sysop-mail").setToAddress("1:500/100"),
                "search-sysop");
        assertEquals(2,
                     ui.searchMessages(admin, Mailbox.OUTBOX, "", "needle", 0, 10).getItemsSize());
        assertTrue(ui.searchMessages(point, Mailbox.OUTBOX, "", "needle", 0, 10)
                     .getItems()
                     .isEmpty());
        assertTrue(ui.searchMessages(point, Mailbox.INBOX, "", "needle", 0, 10)
                     .getItems()
                     .isEmpty());
        ui.sendPendingNetmail(admin);
        var incoming = ui.searchMessages(point, Mailbox.INBOX, "", "needle", 0, 10);
        assertEquals(1, incoming.getItemsSize());
        assertEquals(1,
                     ui.searchMessages(admin, Mailbox.INBOX, "", "needle", 0, 10).getItemsSize());
        ui.deleteMessage(point, Mailbox.INBOX, incoming.getItems().get(0).getId());
        var deleted = ui.searchMessages(point, Mailbox.INBOX, "", "needle", 0, 10);
        assertTrue(deleted.getItems().isEmpty());
        assertEquals(1, deleted.getScanned());
        assertFalse(deleted.isHasMore());
        assertEquals(2,
                     ui.searchMessages(admin, Mailbox.OUTBOX, "", "needle", 0, 10).getItemsSize());
    }

    private void storeSearchText(String area, String id, String text, String subject)
            throws Exception {
        Message message = new Message("Author",
                                      "All",
                                      new FTNAddr("1:500/101"),
                                      null,
                                      subject,
                                      "AREA:" + area + "\r\1MSGID: 1:500/101 " + id + "\r" + text,
                                      new Date());
        base.addMessage(message, area);
    }

    @Test
    void enforcedLiveReadWritePermissionsAndTheExactSenderAka() throws Exception {
        String point = point();
        assertTrue(ui.areas(point).isEmpty());
        ui.subscribe(admin, "1:500/100.1", "test", "READ");
        var area = ui.areas(point).get(0);
        assertTrue(area.isReadable());
        assertTrue(area.getWriteAddresses().isEmpty());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.send(point, draft(), "read-only")).getCode());
        ui.subscribe(admin, "1:500/100.1", "test", "BOTH");
        assertEquals(List.of("1:500/100.1"), ui.areas(point).get(0).getWriteAddresses());
        String id = ui.send(point, draft(), "post-once");
        assertEquals(id, ui.send(point, draft(), "post-once"));
        assertEquals(1, base.getMessageSize("test"));
        long row = ui.messages(point, Mailbox.ECHO, "test", 0, 10).getItems().get(0).getId();
        ui.markRead(point, Mailbox.ECHO, "test", row, true);
        assertFalse(ui.message(admin, Mailbox.ECHO, "test", row).isRead());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.send(point,
                                                draft().setFromAddress("1:500/100.2"),
                                                "forged-aka")).getCode());
        ui.saveDraft(point, draft());
        ui.unsubscribe(admin, "1:500/100.1", "test");
        assertTrue(ui.drafts(point).isEmpty());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.echoContext(point, "test", row, 0, 10)).getCode());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.messages(point, Mailbox.ECHO, "private", 0, 10))
                                                                                           .getCode());
        ui.subscribe(admin, "1:500/100.1", "test", "WRITE");
        assertFalse(ui.areas(point).get(0).isReadable());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.messages(point, Mailbox.ECHO, "test", 0, 10)).getCode());
        ui.send(point, draft().setId("write-only-draft"), "write-only");
        assertEquals(2, base.getMessageSize("test"));
    }

    private void store(String area, String id, String reply) throws Exception {
        Message message = new Message("Author",
                                      "All",
                                      new FTNAddr("1:500/101"),
                                      null,
                                      "A topic",
                                      "AREA:"
                                      + area
                                      + "\r\1MSGID: "
                                      + id
                                      + "\r"
                                      + (reply == null ? "" : "\1REPLY: " + reply + "\r")
                                      + "Plain body\r",
                                      new Date());
        base.addMessage(message, area);
    }

    @Test
    void navigatedExactAreaScopedReplyIdsAndPagedRepliesWithoutExposingKludges() throws Exception {
        String rootId = "1:500/101 root_%!";
        store("test", rootId, null);
        store("test", "1:500/101 child-one", rootId);
        store("test", "1:500/101 child-two", rootId);
        store("private", "1:500/101 foreign", rootId);
        store("test", "1:500/101 orphan", "1:500/101 missing");
        var rows = ui.messages(admin, Mailbox.ECHO, "test", 0, 10).getItems();
        var recent = ui.echoMessages(admin, "test", 0, 2);
        assertTrue(recent.isHasMore());
        assertFalse(recent.getItems().get(0).isSetBody());
        assertTrue(recent.getItems().get(0).isSetExcerpt());
        assertTrue(recent.getItems().get(0).getExcerpt().length() <= 240);
        assertFalse(recent.getItems().get(0).getExcerpt().contains("\1"));
        assertEquals(rows.get(3).getId(), recent.getItems().get(0).getId());
        var older = ui.echoMessages(admin, "test", recent.getNextId(), 2);
        assertEquals(rows.get(0).getId(), older.getItems().get(1).getId());
        assertFalse(older.isHasMore());
        var rootContext = ui.echoContext(admin, "test", rows.get(0).getId(), 0, 1);
        assertEquals(1, rootContext.getReplies().getItemsSize());
        assertTrue(rootContext.getReplies().isHasMore());
        assertEquals(1,
                     ui.echoContext(admin,
                                    "test",
                                    rows.get(0).getId(),
                                    rootContext.getReplies().getNextId(),
                                    1)
                       .getReplies()
                       .getItemsSize());
        var child = ui.echoContext(admin, "test", rows.get(1).getId(), 0, 10);
        assertEquals(rows.get(0).getId(), child.getParent().getId());
        assertEquals(rootId, rows.get(1).getReplyTo());
        String body = ui.message(admin, Mailbox.ECHO, "test", rows.get(1).getId()).getBody();
        assertTrue(body.contains("Plain body"));
        assertFalse(body.contains("\1"));
        assertFalse(body.contains("AREA:"));
        assertTrue(ui.echoContext(admin, "test", rows.get(3).getId(), 0, 10).isMissingParent());
        assertEquals("NOT_FOUND",
                     assertThrows(UiError.class,
                                  () -> ui.message(admin,
                                                   Mailbox.ECHO,
                                                   "private",
                                                   rows.get(0).getId())).getCode());
    }

    @Test
    void selfReplyDidNotCreateAnEndlessConversation() throws Exception {
        store("test", "1:500/101 self", "1:500/101 self");
        long id = ui.echoMessages(admin, "test", 0, 1).getItems().get(0).getId();
        var context = ui.echoContext(admin, "test", id, 0, 10);
        assertFalse(context.isSetParent());
        assertTrue(context.isMissingParent());
        assertTrue(context.getReplies().getItems().isEmpty());
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null)
            connection.close();
        if (rpc != null)
            rpc.close();
        if (mail != null)
            mail.close();
        if (base != null)
            base.close();
    }
}
