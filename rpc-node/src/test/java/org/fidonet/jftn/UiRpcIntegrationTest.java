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
import org.fidonet.db.MailDatabaseFacade;
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
import org.fidonet.jftn.rpc.NetmailRouteInfo;
import org.fidonet.jftn.rpc.NetmailRoutingPolicy;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.RobotRuleChange;
import org.fidonet.jftn.rpc.RobotRuleInfo;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.TosserConfiguration;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.tosser.OutboundSpool;
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
import java.util.Map;

class UiRpcIntegrationTest {
    @TempDir
    Path root;
    private JFtnConfig config;
    private MailDatabaseFacade base;
    private MailRuntime mail;
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
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:uiRpc" + System.nanoTime()));
        assertTrue(base.open());
        base.createArea("test");
        mail = new MailRuntime(config, base);
        connect();
    }

    private void connect() throws Exception {
        connect("ui-secret");
    }

    @Test
    void editsDirectorySourcesAndCrcAcrossTransportsWithoutBreakingConfigurationReload()
            throws Exception {
        close();
        Path fragment = root.resolve("conf.d/30-nodelist.conf");
        Files.createDirectories(fragment.getParent());
        Files.writeString(fragment,
                          "NodeDirectoryEnabled=true\nDirectoryRefreshEnabled=false\n"
                          + "NodeDirectoryPath="
                          + root.resolve("catalog")
                          + "\n"
                          + "DirectorySource=points|BOSS|"
                          + root.resolve("points.lst")
                          + "|0|0|CP866|true\n");
        open("database");
        Map<String, String> command = Map.of("id",
                                             "points",
                                             "format",
                                             "BOSS",
                                             "path",
                                             root.resolve("next-points.lst").toString(),
                                             "zone",
                                             "0",
                                             "net",
                                             "0",
                                             "charset",
                                             "KOI8-R",
                                             "requireChecksum",
                                             "true");
        ui.saveExtension(token,
                         "nodelist",
                         "crc",
                         Map.of("source", "points", "mask", "POINTS.*", "mode", "XMODEM-CRC"));
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            browser.saveExtension(token, "nodelist", "sources", command);
            var directory = browser.extensions(token)
                                   .stream()
                                   .filter(extension -> extension.getId().equals("nodelist"))
                                   .findFirst()
                                   .orElseThrow();
            var sources = directory.getSections()
                                   .stream()
                                   .filter(section -> section.getId().equals("sources"))
                                   .findFirst()
                                   .orElseThrow();
            assertEquals(List.of(command), sources.getRows());
        }
        assertTrue(Files.readString(fragment).contains("KOI8-R|true"));
        assertTrue(settingsClient().reload(token));
        assertEquals("UNAUTHORIZED",
                     assertThrows(UiError.class,
                                  () -> ui.saveExtension("invalid", "nodelist", "sources", command))
                                                                                                    .getCode());
        ProfileService.Client profiles = new ProfileService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                            "profile"));
        String point = approvedPoint(profiles, "directory-point", "1:500/100.1");
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.saveExtension(point, "nodelist", "sources", command))
                                                                                                .getCode());
        Files.writeString(fragment, Files.readString(fragment).replace("KOI8-R", "UTF-8"));
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class, () -> settingsClient().reload(token)).getCode());
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
        template = NodeSessions.create(config);
        rpc = NodeRpc.open(config, base, mail, template);
        transport = new TFramedTransport(new TSocket("127.0.0.1", rpc.getPort(), 5000));
        transport.open();
        LoginService.Client login = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                     "login"));
        token = login.login(new Credential("sysop", password)).getSession_id();
        ui = new UiService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport), "ui"));
    }

    @Test
    void appliedEchoCreationRulesLiveAndRejectedInvalidModesWithoutChangingTheFile()
            throws Exception {
        SettingsService.Client settings = new SettingsService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                              "settings"));
        var links = settings.links(token);
        links.get(0).setAutoCreateEchoAccess("WRITE");
        assertTrue(settings.saveLinks(token, links));
        assertEquals(List.of("1:500/101.0,WRITE"), config.getValuesAsList("AutoCreateEchoFrom"));
        assertEquals("WRITE", settings.links(token).get(0).getAutoCreateEchoAccess());
        byte[] accepted = Files.readAllBytes(root.resolve("jftn.conf"));
        links.get(0).setAutoCreateEchoAccess("READ");
        assertThrows(UiError.class, () -> settings.saveLinks(token, links));
        assertArrayEquals(accepted, Files.readAllBytes(root.resolve("jftn.conf")));
        links.get(0).setAutoCreateEchoAccess("NONE");
        assertTrue(settings.saveLinks(token, links));
        assertTrue(config.getValuesAsList("AutoCreateEchoFrom") == null
                || config.getValuesAsList("AutoCreateEchoFrom").isEmpty());
    }

    @Test
    void addedTwoUplinksThroughRpcWithoutRestartOrChangingExistingSessionSnapshots()
            throws Exception {
        SettingsService.Client settings = new SettingsService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                              "settings"));
        SessionContext existing = new SessionContext(template);
        var links = settings.links(token);
        for (int node : List.of(102, 103)) {
            links.add(new MailLinkConfiguration("1:500/" + node,
                                                List.of("1:500/100"),
                                                "peer" + node + ".example.org",
                                                24554,
                                                "long-session-password",
                                                "pktpass",
                                                "SEPARATE",
                                                "NONE"));
        }
        assertTrue(settings.saveLinks(token, links));
        assertEquals(3, settings.links(token).size());
        assertEquals(3, new SessionContext(template).getLinksInfo().getLinks().size());
        assertEquals(1, existing.getLinksInfo().getLinks().size());
        var added = config.getLink(new FTNAddr("1:500/103"));
        assertEquals("long-session-password", added.getPass());
        assertEquals("pktpass", config.getPacketPassword(added));
        assertFalse(base.canReadEcho(added, "test"));
        assertTrue(settings.links(token)
                           .stream()
                           .allMatch(link -> link.getSessionPassword().isEmpty()
                                   && link.getPacketPassword().isEmpty()));
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
                                             NodeSessions.create(config),
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
            rpc = NodeRpc.open(config, base, mail, NodeSessions.create(config), shared);
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
    void deletedWaitingMailCannotBeSentAndPreparedPacketsSurviveMailboxDeletion() throws Exception {
        ui.send(token, draft(Mailbox.OUTBOX).setSubject("Cancel before send"), "cancel-waiting");
        long cancelled = ui.messages(token, Mailbox.OUTBOX, "", 0, 10).getItems().get(0).getId();
        ui.deleteMessage(token, Mailbox.OUTBOX, cancelled);
        ui.deleteMessage(token, Mailbox.OUTBOX, cancelled);
        assertEquals(0, ui.pendingNetmail(token));
        assertEquals(0, ui.sendPendingNetmail(token));
        assertTrue(ui.messages(token, Mailbox.OUTBOX, "", 0, 10).getItems().isEmpty());
        assertTrue(ui.outbound(token, "1:500/101", 10).isEmpty());
        ui.send(token, draft(Mailbox.OUTBOX).setSubject("Already prepared"), "keep-packet");
        assertEquals(1, ui.sendPendingNetmail(token));
        var link = config.getLink(new FTNAddr("1:500/101"));
        OutboundSpool spool = new OutboundSpool(config);
        Path packet = spool.pending(link, true).get(0);
        byte[] bytes = Files.readAllBytes(packet);
        long sent = ui.messages(token, Mailbox.OUTBOX, "", 0, 10).getItems().get(0).getId();
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            browser.deleteMessage(token, Mailbox.OUTBOX, sent);
            assertEquals(0, browser.pendingNetmail(token));
            assertEquals("NOT_FOUND",
                         assertThrows(UiError.class,
                                      () -> browser.message(token, Mailbox.OUTBOX, "", sent))
                                                                                             .getCode());
        }
        ui.processMail(token);
        assertArrayEquals(bytes, Files.readAllBytes(packet));
        assertEquals(List.of(packet), spool.pending(link, true));
        mail.operate(tosser -> {
            tosser.acknowledgeOutbound(spool.acknowledge(packet));
            return null;
        });
        assertTrue(spool.pending(link, true).isEmpty());
    }

    @Test
    void mailboxDeletionIsScopedAndDoesNotRemoveTheLocalRecipientsCopy() throws Exception {
        ProfileService.Client profiles = new ProfileService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                            "profile"));
        String point = approvedPoint(profiles, "delete-point", "1:500/100.1");
        ui.send(token, draft(Mailbox.OUTBOX).setToAddress("1:500/100.1"), "local-copy");
        long id = ui.messages(token, Mailbox.OUTBOX, "", 0, 10).getItems().get(0).getId();
        assertEquals("NOT_FOUND",
                     assertThrows(UiError.class,
                                  () -> ui.deleteMessage(point, Mailbox.OUTBOX, id)).getCode());
        assertEquals(1, ui.sendPendingNetmail(token));
        ui.deleteMessage(token, Mailbox.OUTBOX, id);
        assertEquals(1, ui.messages(point, Mailbox.INBOX, "", 0, 10).getItemsSize());
        ui.deleteMessage(point, Mailbox.INBOX, id);
        assertTrue(ui.messages(point, Mailbox.INBOX, "", 0, 10).getItems().isEmpty());
        transport.close();
        rpc.close();
        connect();
        assertTrue(ui.messages(token, Mailbox.OUTBOX, "", 0, 10).getItems().isEmpty());
    }

    private String approvedPoint(ProfileService.Client profiles, String login, String address)
            throws Exception {
        profiles.register(login, "point-password-123", address);
        profiles.decideAka(token, login, address, true);
        LoginService.Client logins = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                      "login"));
        return logins.login(new Credential(login, "point-password-123")).getSession_id();
    }

    private SettingsService.Client settingsClient() {
        return new SettingsService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                   "settings"));
    }

    private NetmailRoutingPolicy databaseRouting() throws Exception {
        NetmailRoutingPolicy policy = new NetmailRoutingPolicy("database",
                                                               "1:500/101",
                                                               false,
                                                               List.of(),
                                                               List.of());
        settingsClient().saveNetmailRouting(token, policy);
        ui.saveNetmailRoute(token, new NetmailRouteInfo("zone-two", 10, "2:*", "1:500/101", true));
        return policy;
    }

    @Test
    void managedRoutingLiveWithPreviewValidationAndPointDenial() throws Exception {
        NetmailRoutingPolicy policy = databaseRouting();
        byte[] accepted = Files.readAllBytes(root.resolve("jftn.conf"));
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> settingsClient().saveNetmailRouting(token,
                                                                            policy.deepCopy()
                                                                                  .setTransitFrom(List.of("invalid")))).getCode());
        assertArrayEquals(accepted, Files.readAllBytes(root.resolve("jftn.conf")));
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> ui.saveNetmailRoute(token,
                                                            new NetmailRouteInfo("bad",
                                                                                 0,
                                                                                 "*",
                                                                                 "1:500/999",
                                                                                 false))).getCode());
        ProfileService.Client profiles = new ProfileService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                            "profile"));
        String point = approvedPoint(profiles, "routing-point", "1:500/100.1");
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class, () -> ui.netmailRoutes(point)).getCode());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> settingsClient().saveNetmailRouting(point, policy))
                                                                                           .getCode());
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            assertEquals("RULE",
                         browser.previewNetmailRoute(token, "2:500/100", "", false).getReason());
            assertEquals("FORBIDDEN",
                         assertThrows(UiError.class,
                                      () -> browser.deleteNetmailRoute(point, "zone-two"))
                                                                                          .getCode());
        }
        assertTrue(base.getQueuedNetmail().isEmpty());
    }

    @Test
    void previewedRealRoutingAndLiveTransitMasksWithoutCreatingMail() throws Exception {
        NetmailRoutingPolicy policy = databaseRouting();
        var result = ui.previewNetmailRoute(token, "2:500/100", "", false);
        assertEquals("RULE", result.getReason());
        assertEquals("zone-two", result.getRuleId());
        assertEquals("1:500/101.0", result.getNextHop());
        assertEquals("LOCAL", ui.previewNetmailRoute(token, "1:500/100", "", false).getReason());
        assertEquals("DIRECT_UNAVAILABLE",
                     ui.previewNetmailRoute(token, "2:500/100", "", true).getReason());
        assertEquals("TRANSIT_DISABLED",
                     ui.previewNetmailRoute(token, "2:500/100", "1:500/101", false).getReason());
        settingsClient().saveNetmailRouting(token,
                                            policy.deepCopy()
                                                  .setAllowTransit(true)
                                                  .setTransitFrom(List.of("1:500/999")));
        assertEquals("SOURCE_DENIED",
                     ui.previewNetmailRoute(token, "2:500/100", "1:500/101", false).getReason());
        settingsClient().saveNetmailRouting(token,
                                            policy.deepCopy()
                                                  .setAllowTransit(true)
                                                  .setTransitFrom(List.of("1:500/101")));
        assertEquals("RETURN_TO_SOURCE",
                     ui.previewNetmailRoute(token, "2:500/100", "1:500/101", false).getReason());
        assertTrue(base.getQueuedNetmail().isEmpty());
    }

    @Test
    void routingChangesPreservedQueuedNextHopsAndSurvivedRpcRestart() throws Exception {
        NetmailRoutingPolicy policy = databaseRouting();
        ui.send(token, draft(Mailbox.OUTBOX).setToAddress("2:500/100"), "retained-hop");
        transport.close();
        rpc.close();
        connect();
        assertEquals(1, ui.netmailRoutes(token).getRulesSize());
        ui.deleteNetmailRoute(token, "zone-two");
        assertEquals("DEFAULT", ui.previewNetmailRoute(token, "2:500/100", "", false).getReason());
        assertEquals(0, ui.netmailRoutes(token).getRulesSize());
        settingsClient().saveNetmailRouting(token, policy.deepCopy().setDefaultHop(""));
        assertEquals("NO_ROUTE", ui.previewNetmailRoute(token, "2:500/100", "", false).getReason());
        assertEquals("NO_ROUTE",
                     assertThrows(UiError.class,
                                  () -> ui.send(token,
                                                draft(Mailbox.OUTBOX).setToAddress("2:500/100"),
                                                "no-route-send")).getCode());
        assertEquals(1, ui.pendingNetmail(token));
        ui.sendPendingNetmail(token);
        assertEquals(1, base.getQueuedNetmail().size());
        assertEquals("1:500/101.0", base.getQueuedNetmail().get(0).getNextHop());
    }
    @Test
    void isolatedApprovedPointMailboxesAndSendersAcrossTcpAndHttp() throws Exception {
        ProfileService.Client profiles = new ProfileService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                            "profile"));
        String first = approvedPoint(profiles, "point-one", "1:500/100.1");
        String second = approvedPoint(profiles, "point-two", "1:500/100.2");
        ui.send(token, draft(Mailbox.OUTBOX).setToAddress("1:500/100.1"), "to-first");
        ui.send(token, draft(Mailbox.OUTBOX).setToAddress("1:500/100.2"), "to-second");
        assertTrue(ui.messages(first, Mailbox.INBOX, "", 0, 10).getItems().isEmpty());
        assertEquals(2, ui.sendPendingNetmail(token));
        assertTrue(ui.messages(token, Mailbox.OUTBOX, "", 0, 10)
                     .getItems()
                     .stream()
                     .allMatch(item -> (item.getAttributes() & 8) != 0));
        MailPage firstInbox = ui.messages(first, Mailbox.INBOX, "", 0, 10);
        MailPage secondInbox = ui.messages(second, Mailbox.INBOX, "", 0, 10);
        assertEquals(1, firstInbox.getItemsSize());
        assertEquals(1, secondInbox.getItemsSize());
        var received = ui.message(first, Mailbox.INBOX, "", firstInbox.getItems().get(0).getId());
        assertFalse(received.getBody().contains("\1"));
        assertTrue(received.getBody().contains("Hello from UI"));
        long foreignId = secondInbox.getItems().get(0).getId();
        assertEquals("NOT_FOUND",
                     assertThrows(UiError.class,
                                  () -> ui.message(first, Mailbox.INBOX, "", foreignId)).getCode());
        assertEquals("NOT_FOUND",
                     assertThrows(UiError.class,
                                  () -> ui.markRead(first, Mailbox.INBOX, "", foreignId, true))
                                                                                               .getCode());
        assertEquals("FORBIDDEN", assertThrows(UiError.class, () -> ui.status(first)).getCode());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.messages(first, Mailbox.ECHO, "test", 0, 10)).getCode());
        MailDraft pointDraft = draft(Mailbox.OUTBOX).setFromAddress("1:500/100.1")
                                                    .setBody("> " + received.getBody())
                                                    .setReplyTo(received.getMessageId());
        ui.saveDraft(first, pointDraft);
        assertTrue(ui.drafts(second).isEmpty());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.send(first,
                                                pointDraft.deepCopy().setFromAddress("1:500/100.2"),
                                                "forged-point")).getCode());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> ui.saveDraft(first,
                                                     pointDraft.deepCopy()
                                                               .setFromAddress("1:500/100"))).getCode());
        String msgid = ui.send(first, pointDraft, "point-send");
        assertEquals(msgid, ui.send(first, pointDraft, "point-send"));
        assertEquals(1, ui.pendingNetmail(first));
        assertEquals(0, ui.pendingNetmail(second));
        assertEquals(0, ui.sendPendingNetmail(second));
        assertTrue(base.getQueuedNetmail().isEmpty());
        assertEquals(1, ui.sendPendingNetmail(first));
        assertEquals(received.getMessageId(),
                     base.getQueuedNetmail()
                         .get(0)
                         .getMessage()
                         .getBody()
                         .getKludges()
                         .get("REPLY")
                         .trim());
        assertEquals(1, ui.messages(first, Mailbox.OUTBOX, "", 0, 10).getItemsSize());
        assertTrue(ui.messages(second, Mailbox.OUTBOX, "", 0, 10).getItems().isEmpty());
        try (THttpClient connection = new THttpClient("http://127.0.0.1:" + rpc.getHttpPort()
                + "/rpc/ui")) {
            UiService.Client browser = new UiService.Client(new TJSONProtocol(connection));
            assertEquals(1, browser.messages(first, Mailbox.INBOX, "", 0, 10).getItemsSize());
            assertEquals("NOT_FOUND",
                         assertThrows(UiError.class,
                                      () -> browser.message(first, Mailbox.INBOX, "", foreignId))
                                                                                                 .getCode());
        }
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
        assertTrue(id.matches("1:500/100 [0-9a-f]{8}"));
        transport.close();
        rpc.close();
        connect();
        assertEquals(id, ui.send(token, draft, "request-1"));
        assertEquals(1, ui.pendingNetmail(token));
        ui.processMail(token);
        assertTrue(ui.outbound(token, "1:500/101", 10).isEmpty());
        assertTrue(base.getQueuedNetmail().isEmpty());
        assertEquals(1, ui.sendPendingNetmail(token));
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
        assertEquals("FORBIDDEN",
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
        Files.writeString(root.resolve("node.log"), "INFO password=secret\nINFO PWD [redacted]\n");
        var tail = ui.logs(token, -1, 2);
        assertTrue(tail.getLines().get(0).contains("[REDACTED]"));
        assertTrue(tail.getLines().stream().noneMatch(line -> line.contains("secret")));
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
