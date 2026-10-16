package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.MailPage;
import org.fidonet.jftn.rpc.Mailbox;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

class UiRpcIntegrationTest {
    @TempDir
    Path root;
    private JFtnConfig config;
    private DatabaseManager base;
    private MailService mail;
    private NodeRpc rpc;
    private TFramedTransport transport;
    private UiService.Client ui;
    private String token;

    @BeforeEach
    void open() throws Exception {
        String path = root.toString().replace('\\', '/');
        String text = "SysOp=Test\nInbound=" + path + "/in\nOutbound=" + path + "/out\nTmp=" + path
                + "/state\nEchoPath=" + path + "/echo\n"
                + "Link=1:500/101,1:500/100,secret,127.0.0.1\nJamBridgeEnabled=false\n"
                + "EchoSubscriptions=database\nRpcEnabled=true\nRpcPort=0\nRpcUser=sysop\nRpcPassword=ui-secret\n"
                + "RpcHttpEnabled=true\nRpcHttpPort=0\nRpcHttpOrigin=http://localhost:5173\n"
                + "RpcLogFile=" + path + "/node.log\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:uiRpc" + System.nanoTime()));
        assertTrue(base.open());
        base.createArea("test");
        mail = new MailService(config, base);
        connect();
    }

    private void connect() throws Exception {
        connect("ui-secret");
    }

    private void connect(String password) throws Exception {
        rpc = NodeRpc.open(config, base, mail, JFtnRunner.createSessionContext(config));
        transport = new TFramedTransport(new TSocket("127.0.0.1", rpc.getPort(), 5000));
        transport.open();
        LoginService.Client login = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                     "login"));
        token = login.login(new Credential("sysop", password)).getSession_id();
        ui = new UiService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport), "ui"));
    }

    @Test
    void exposesTheSameAuthenticatedBackendOverHttpAndTcp() throws Exception {
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            connection.setReadTimeout(5000);
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            assertEquals(ui.status(token).getLocalAddresses(),
                         browser.status(token).getLocalAddresses());
            assertEquals("UNAUTHORIZED",
                         assertThrows(UiError.class, () -> browser.status("bad")).getCode());
        }
    }

    @Test
    void changesDatabasePasswordWithoutLettingBootstrapConfigurationResetIt() throws Exception {
        AuthService.Client auth = new AuthService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                  "auth"));
        assertTrue(auth.methods().isPassword());
        assertFalse(auth.methods().isPasskey());
        auth.changePassword(token, "ui-secret", "replacement-ui-password");
        assertEquals("UNAUTHORIZED", assertThrows(UiError.class, () -> ui.status(token)).getCode());
        transport.close();
        rpc.close();
        connect("replacement-ui-password");
        assertEquals("2.0", ui.status(token).getApiVersion());
    }

    @AfterEach
    void close() throws Exception {
        if (transport != null) {
            transport.close();
        }
        if (rpc != null) {
            rpc.close();
        }
        if (mail != null) {
            mail.close();
        }
        if (base != null) {
            base.close();
        }
    }

    private MailDraft draft(Mailbox mailbox) {
        return new MailDraft("draft",
                             mailbox,
                             mailbox == Mailbox.ECHO ? "test" : "",
                             "Sysop",
                             "Reader",
                             "1:500/100",
                             "1:500/101",
                             "Subject",
                             "Hello from UI");
    }

    @Test
    void sendsNetmailIdempotentlyPersistsDraftsAndDoesNotExposePasswords() throws Exception {
        MailDraft draft = draft(Mailbox.OUTBOX);
        ui.saveDraft(token, draft);
        assertEquals(1, ui.drafts(token).size());
        transport.close();
        rpc.close();
        connect();
        assertEquals(draft, ui.drafts(token).get(0));
        String id = ui.send(token, draft, "request-1");
        assertEquals(id, ui.send(token, draft, "request-1"));
        assertEquals(1, base.getQueuedNetmail().size());
        assertTrue(ui.drafts(token).isEmpty());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.send(token,
                                                draft.deepCopy().setBody("changed"),
                                                "request-1")).getCode());
        assertFalse(ui.links(token).toString().contains("secret"));
        ui.processMail(token);
        assertEquals(1, ui.outbound(token, "1:500/101", 10).size());
        assertEquals(1, ui.messages(token, Mailbox.OUTBOX, "", 0, 10).getItemsSize());
    }

    @Test
    void savesIncompleteDraftsButRejectsSendingThem() throws Exception {
        MailDraft draft = draft(Mailbox.OUTBOX).setToName("").setToAddress("").setSubject("");
        ui.saveDraft(token, draft);
        assertEquals(draft, ui.drafts(token).get(0));
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.send(token, draft, "incomplete")).getCode());
        ui.deleteDraft(token, draft.getId());
        assertTrue(ui.drafts(token).isEmpty());
    }

    @Test
    void readsPagesAndMessageDetailsMaintainsReadMarksAndAdministersSubscriptions()
            throws Exception {
        ui.subscribe(token, "1:500/101", "test", "BOTH");
        for (int i = 0; i < 3; i++) {
            ui.send(token, draft(Mailbox.ECHO).setSubject("Subject " + i), "echo-" + i);
        }
        assertEquals(3, base.getMessageSize("test"));
        MailPage first = ui.messages(token, Mailbox.ECHO, "TEST", 0, 2);
        assertTrue(first.isHasMore());
        assertEquals(2, first.getItemsSize());
        assertFalse(first.getItems().get(0).isSetBody());
        long id = first.getItems().get(0).getId();
        assertTrue(ui.message(token, Mailbox.ECHO, "test", id).getBody().contains("Hello from UI"));
        ui.markRead(token, Mailbox.ECHO, "test", id, true);
        assertTrue(ui.message(token, Mailbox.ECHO, "test", id).isRead());
        assertEquals(1,
                     ui.messages(token, Mailbox.ECHO, "test", first.getNextId(), 2).getItemsSize());
        ui.rescan(token, "1:500/101", "test", 1, "rescan-1");
        ui.unsubscribe(token, "1:500/101", "test");
        assertFalse(base.canReadEcho(config.getLink(new FTNAddr("1:500/101")), "test"));
        assertEquals("NOT_FOUND",
                     assertThrows(UiError.class,
                                  () -> ui.message(token, Mailbox.INBOX, "", id)).getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.messages(token, Mailbox.ECHO, "test", 0, 201))
                                                                                         .getCode());
    }

    @Test
    void readsLocalNetmailAndLogsAndRejectsForgedLocalAddressesOrEmbeddedControls()
            throws Exception {
        Message message = new Message("Remote",
                                      "Sysop",
                                      new FTNAddr("1:500/101"),
                                      new FTNAddr("1:500/100"),
                                      "Incoming",
                                      "Hello\r",
                                      new Date());
        base.saveNetmail(message, "incoming", null);
        assertEquals(1, ui.messages(token, Mailbox.INBOX, "", 0, 10).getItemsSize());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.send(token,
                                                draft(Mailbox.OUTBOX).setFromAddress("1:500/999"),
                                                "forged")).getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.send(token,
                                                draft(Mailbox.OUTBOX).setBody("\1INTL forged\r"),
                                                "control")).getCode());
        Files.writeString(root.resolve("node.log"), "First\nSecond\n", StandardCharsets.UTF_8);
        var first = ui.logs(token, 0, 1);
        assertEquals(1, first.getLinesSize());
        assertEquals("Second", ui.logs(token, first.getNextOffset(), 1).getLines().get(0));
        Files.writeString(root.resolve("node.log"), "New\n");
        assertTrue(ui.logs(token, 99, 1).isRotated());
        assertEquals("2.0", ui.status(token).getApiVersion());
    }

    @Test
    void readsArchivedEchoWithoutDestinationAddressAndRejectsOutOfRangeAddresses()
            throws Exception {
        Message message = new Message("Author",
                                      "All",
                                      new FTNAddr("1:500/100"),
                                      null,
                                      "Archive",
                                      "AREA:TEST\rHistorical mail\r",
                                      new Date());
        mail.getTosser().submitEchomail(message);
        MailPage page = ui.messages(token, Mailbox.ECHO, "test", 0, 10);
        assertEquals("", page.getItems().get(0).getToAddress());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.send(token,
                                                draft(Mailbox.OUTBOX).setToAddress("1:500/999999"),
                                                "range")).getCode());
    }
}
