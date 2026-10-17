package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.MailLinkConfiguration;
import org.fidonet.jftn.rpc.MailPage;
import org.fidonet.jftn.rpc.Mailbox;
import org.fidonet.jftn.rpc.RobotRuleChange;
import org.fidonet.jftn.rpc.RobotRuleInfo;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.TosserConfiguration;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

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
    private SessionContext template;

    @BeforeEach
    void open() throws Exception {
        open("database");
    }

    private void open(String echoMode) throws Exception {
        String path = root.toString().replace('\\', '/');
        String text = "SysOp=Test\nInbound=" + path + "/in\nOutbound=" + path + "/out\nTmp=" + path
                + "/state\nEchoPath=" + path + "/echo\n"
                + "Link=1:500/101,1:500/100,secret,127.0.0.1\nJamBridgeEnabled=false\n"
                + "EchoSubscriptions=" + echoMode
                + "\nRpcEnabled=true\nRpcPort=0\nRpcUser=sysop\nRpcPassword=ui-secret\n"
                + "RpcHttpEnabled=true\nRpcHttpPort=0\nRpcHttpOrigin=http://localhost:5173\n"
                + "RpcLogFile=" + path + "/node.log\n";
        config = new JFtnConfig();
        Path source = root.resolve("jftn.conf");
        Files.writeString(source, text);
        config.load(source.toString());
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:uiRpc" + System.nanoTime()));
        assertTrue(base.open());
        base.createArea("test");
        mail = new MailService(config, base);
        connect();
    }

    private void connect() throws Exception {
        connect("ui-secret");
    }

    @Test
    void releasesAndArchivesQuarantineOverTcpAndHttpWithSnapshotAndPeerChecks() throws Exception {
        Path state = Files.createDirectories(root.resolve("state"));
        byte[] bytes = quarantinePacket();
        Files.write(state.resolve("legacy.bad"), bytes);
        var item = ui.quarantine(token, "", 25).getItems().get(0);
        assertEquals("", item.getPeer());
        assertEquals("UNAUTHORIZED",
                     assertThrows(UiError.class, () -> ui.quarantine("invalid", "", 25)).getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.retryQuarantine(token,
                                                           item.getId(),
                                                           item.getChecksum(),
                                                           "")).getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.archiveQuarantine(token,
                                                             "../legacy.bad",
                                                             item.getChecksum())).getCode());
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            assertTrue(browser.retryQuarantine(token, item.getId(), item.getChecksum(), "1:500/101")
                              .isAccepted());
            assertTrue(ui.quarantine(token, "", 25).getItems().isEmpty());
            assertTrue(Files.exists(state.resolve(".quarantine/archive/"
                                                  + item.getChecksum()
                                                  + ".bad")));
            Files.write(state.resolve("archive.bad"), bytes);
            var archived = browser.quarantine(token, "", 25).getItems().get(0);
            bytes[8] ^= 1;
            Files.write(state.resolve("archive.bad"), bytes);
            assertEquals("INVALID_ARGUMENT",
                         assertThrows(UiError.class,
                                      () -> browser.archiveQuarantine(token,
                                                                      archived.getId(),
                                                                      archived.getChecksum())).getCode());
            var changed = browser.quarantine(token, "", 25).getItems().get(0);
            browser.archiveQuarantine(token, changed.getId(), changed.getChecksum());
            assertTrue(ui.quarantine(token, "", 25).getItems().isEmpty());
        }
        assertTrue(base.getLocalNetmail(new FTNAddr("1:500/100")).isEmpty());
    }

    private byte[] quarantinePacket() {
        ByteBuffer packet = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort(0, (short) 101);
        packet.putShort(2, (short) 100);
        packet.putShort(18, (short) 2);
        packet.putShort(20, (short) 500);
        packet.putShort(22, (short) 500);
        packet.putShort(34, (short) 1);
        packet.putShort(36, (short) 1);
        packet.position(26);
        packet.put("secret".getBytes(StandardCharsets.US_ASCII));
        return packet.array();
    }

    @Test
    void managesEchoImportAndExportRightsAcrossTcpAndHttpWithoutDeletingMail() throws Exception {
        assertEquals("database", ui.echoAccess(token).getSource());
        assertTrue(ui.echoAccess(token).getSubscriptions().isEmpty());
        ui.subscribe(token, "1:500/101", "NEW.ECHO", "WRITE");
        var link = config.getLink(new FTNAddr("1:500/101"));
        assertTrue(base.isAreaExists("new.echo"));
        assertTrue(base.canWriteEcho(link, "new.echo"));
        assertFalse(base.canReadEcho(link, "new.echo"));
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            var snapshot = browser.echoAccess(token);
            assertEquals("WRITE", snapshot.getSubscriptions().get(0).getAccess());
            assertEquals("new.echo", snapshot.getSubscriptions().get(0).getArea());
            assertTrue(snapshot.getAreas().contains("new.echo"));
            assertTrue(snapshot.getPeers().contains(link.getAddr().toString()));
            browser.subscribe(token, "1:500/101", "new.echo", "READ");
            assertFalse(base.canWriteEcho(link, "new.echo"));
            assertTrue(base.canReadEcho(link, "new.echo"));
            assertEquals("READ", ui.echoAccess(token).getSubscriptions().get(0).getAccess());
            browser.subscribe(token, "1:500/101", "new.echo", "BOTH");
            assertTrue(base.canWriteEcho(link, "new.echo"));
            ui.send(token, draft(Mailbox.ECHO).setArea("new.echo"), "retained-echo");
            assertEquals(1, base.getMessageSize("new.echo"));
            browser.unsubscribe(token, "1:500/101", "new.echo");
        }
        assertTrue(ui.echoAccess(token).getSubscriptions().isEmpty());
        assertFalse(base.canWriteEcho(link, "new.echo"));
        assertFalse(base.canReadEcho(link, "new.echo"));
        assertEquals(1, base.getMessageSize("new.echo"));
        assertTrue(ui.echoAccess(token).getAreas().contains("new.echo"));
        assertEquals("UNAUTHORIZED",
                     assertThrows(UiError.class, () -> ui.echoAccess("invalid")).getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.subscribe(token, "1:500/999", "new.echo", "BOTH"))
                                                                                             .getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.subscribe(token, "1:500/101", "new.echo", "INVALID"))
                                                                                                .getCode());
    }

    @Test
    void fileManagedEchoPermissionsRemainReadOnlyOverRpc() throws Exception {
        close();
        open("config");
        assertEquals("config", ui.echoAccess(token).getSource());
        assertEquals("READ_ONLY",
                     assertThrows(UiError.class,
                                  () -> ui.subscribe(token, "1:500/101", "test", "BOTH"))
                                                                                         .getCode());
        assertEquals("READ_ONLY",
                     assertThrows(UiError.class,
                                  () -> ui.unsubscribe(token, "1:500/101", "test")).getCode());
        assertTrue(ui.echoAccess(token).getSubscriptions().isEmpty());
    }

    private void connect(String password) throws Exception {
        template = JFtnRunner.createSessionContext(config);
        rpc = NodeRpc.open(config, base, mail, template);
        transport = new TFramedTransport(new TSocket("127.0.0.1", rpc.getPort(), 5000));
        transport.open();
        LoginService.Client login = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                     "login"));
        token = login.login(new Credential("sysop", password)).getSession_id();
        ui = new UiService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport), "ui"));
    }

    @Test
    void hotReloadsSettingsAndPacketPasswordsWithoutLosingSessionsAndRejectsInvalidCandidates()
            throws Exception {
        SettingsService.Client settings = new SettingsService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                              "settings"));
        settings.saveTosser(token, new TosserConfiguration(7, false, true, true));
        assertEquals("7", config.getValue("TossIntervalSeconds"));
        assertEquals("true", config.getValue("AutoCreateEcho"));
        SessionContext existing = new SessionContext(template);
        String originalName = existing.getStationConfig().getName();
        var station = settings.current(token);
        station.setSystem("Reloaded station");
        settings.save(token, station);
        assertEquals("Reloaded station", new SessionContext(template).getStationConfig().getName());
        assertEquals(originalName, existing.getStationConfig().getName());
        byte[] accepted = Files.readAllBytes(root.resolve("jftn.conf"));
        station.setSystem("Must not apply").setBrowserOrigin("http://localhost:9998");
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class, () -> settings.save(token, station)).getCode());
        assertArrayEquals(accepted, Files.readAllBytes(root.resolve("jftn.conf")));
        assertEquals("Reloaded station", config.getValue("System"));
        var links = settings.links(token);
        links.get(0).setPacketPasswordMode("SEPARATE").setPacketPassword("wrong");
        settings.saveLinks(token, links);
        Path state = Files.createDirectories(root.resolve("state"));
        Files.write(state.resolve("retry.bad"), quarantinePacket());
        var item = ui.quarantine(token, "", 25).getItems().get(0);
        assertFalse(ui.retryQuarantine(token, item.getId(), item.getChecksum(), "1:500/101")
                      .isAccepted());
        links.get(0).setPacketPassword("secret");
        settings.saveLinks(token, links);
        assertTrue(ui.retryQuarantine(token, item.getId(), item.getChecksum(), "1:500/101")
                     .isAccepted());
        byte[] valid = Files.readAllBytes(root.resolve("jftn.conf"));
        Files.writeString(root.resolve("jftn.conf"),
                          new String(valid,
                                     StandardCharsets.UTF_8).replace("autocreateecho=true",
                                                                     "autocreateecho=invalid"));
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class, () -> settings.reload(token)).getCode());
        assertEquals("true", config.getValue("AutoCreateEcho"));
        Files.writeString(root.resolve("jftn.conf"), "broken syntax");
        assertEquals("INVALID_CONFIGURATION",
                     assertThrows(UiError.class, () -> settings.reload(token)).getCode());
        Files.writeString(root.resolve("jftn.conf"),
                          new String(valid,
                                     StandardCharsets.UTF_8).replace("tossintervalseconds=7",
                                                                     "tossintervalseconds=9"));
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/settings")) {
            assertTrue(new SettingsService.Client(new TJSONProtocol(connection)).reload(token));
        }
        assertEquals("9", config.getValue("TossIntervalSeconds"));
        assertEquals("UNAUTHORIZED",
                     assertThrows(UiError.class, () -> settings.reload("bad")).getCode());
        assertFalse(ui.status(token).getLocalAddresses().isEmpty());
    }

    @Test
    void exposedPagedPollHistoryAndPerAttemptLogsAcrossBothTransports() throws Exception {
        transport.close();
        rpc.close();
        PollService shared = new PollService(config,
                                             JFtnRunner.createSessionContext(config),
                                             link -> new Client(link) {
                                                 @Override
                                                 public void run(SessionContext context) {
                                                     context.setState(SessionState.STATE_WAITOK);
                                                     context.setState(SessionState.STATE_TRANSFER);
                                                     context.setSendingIsFinish(true);
                                                     context.setReceivingIsFinish(true);
                                                     context.setState(SessionState.STATE_END);
                                                 }
                                                 @Override
                                                 public void stop() {
                                                 }
                                             });
        try (shared) {
            rpc = NodeRpc.open(config, base, mail, JFtnRunner.createSessionContext(config), shared);
            transport = new TFramedTransport(new TSocket("127.0.0.1", rpc.getPort(), 5000));
            transport.open();
            LoginService.Client login = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                         "login"));
            token = login.login(new Credential("sysop", "ui-secret")).getSession_id();
            ui = new UiService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                               "ui"));
            String id = ui.poll(token, "1:500/101").getId();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (shared.get(id).isActive() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                    + "/rpc/ui")) {
                UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
                assertEquals("SUCCEEDED", browser.pollDetails(token, id).getState());
                assertEquals("MANUAL", browser.pollDetails(token, id).getSource());
                assertTrue(browser.pollDetails(token, id).getStarted() > 0);
                assertEquals(id, browser.pollHistory(token, "", 1).getItems().get(0).getId());
                String cursor = browser.pollHistory(token, "", 1).getNextCursor();
                assertTrue(browser.pollHistory(token, cursor, 1).getItems().isEmpty());
                assertEquals(1, browser.pollLog(token, id, 0, 1).getLinesSize());
                long next = browser.pollLog(token, id, 0, 1).getNextOffset();
                assertTrue(browser.pollLog(token, id, next, 100)
                                  .getLines()
                                  .stream()
                                  .anyMatch(line -> line.contains("STATE_TRANSFER")));
                assertTrue(browser.pollQueue(token).isEmpty());
                assertEquals("UNAUTHORIZED",
                             assertThrows(UiError.class,
                                          () -> browser.pollLog("bad", id, 0, 10)).getCode());
                assertEquals("INVALID_ARGUMENT",
                             assertThrows(UiError.class,
                                          () -> browser.pollHistory(token, "invalid", 10))
                                                                                          .getCode());
            }
            rpc.close();
        }
    }

    @Test
    void managesRobotRulesAcrossHttpAndTcpWithoutReturningCredentials() throws Exception {
        RobotRuleInfo rule = new RobotRuleInfo("AreaFix", "1:500/101", "test.*", "READ", true);
        ui.saveRobotRule(token, new RobotRuleChange(rule).setPassword("robot-secret"));
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            assertEquals("database", browser.robotAccess(token).getSource());
            assertEquals(rule.deepCopy().setAddress(new FTNAddr(rule.getAddress()).toString()),
                         browser.robotAccess(token).getRules().get(0));
            assertFalse(browser.robotAccess(token).toString().contains("robot-secret"));
            assertFalse(browser.robotAccess(token).toString().contains("pbkdf2"));
            browser.saveRobotRule(token, new RobotRuleChange(rule.deepCopy().setEnabled(false)));
            assertFalse(ui.robotAccess(token).getRules().get(0).isEnabled());
            browser.saveRobotRule(token, new RobotRuleChange(rule.deepCopy().setAccess("BOTH")));
            assertTrue(base.getRobotRules()
                           .find(RobotService.AreaFix, new FTNAddr("1:500/101"))
                           .verifyPassword("robot-secret"));
            browser.deleteRobotRule(token, "AreaFix", "1:500/101");
            assertTrue(ui.robotAccess(token).getRules().isEmpty());
        }
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.saveRobotRule(token, new RobotRuleChange(rule)))
                                                                                           .getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.saveRobotRule(token,
                                                         new RobotRuleChange(rule.deepCopy()
                                                                                 .setAddress("1:500/999")).setPassword("secret"))).getCode());
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
