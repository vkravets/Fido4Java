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
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.ui.NodeRpc;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.transport.THttpClient;
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
