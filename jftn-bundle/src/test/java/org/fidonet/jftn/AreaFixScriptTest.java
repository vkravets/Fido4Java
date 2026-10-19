package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.db.robots.RobotService;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class AreaFixScriptTest {
    @TempDir
    Path root;
    private MailDatabaseFacade base;
    private JFtnConfig config;
    private Link peer;
    private Tosser tosser;
    private MailAutomationRuntime scripts;

    @BeforeEach
    void open() throws Exception {
        config = configuration("files");
        Files.createDirectories(Path.of(config.getTmpDir()));
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:areafix" + System.nanoTime()));
        assertTrue(base.open());
        peer = config.getLink(new FTNAddr("1:500/101"));
        base.configureLink(peer, "secret");
        base.createArea("test.one");
        base.createArea("private");
        tosser = new Tosser(config, base);
        scripts = new MailAutomationRuntime(config, base, tosser);
    }

    private JFtnConfig configuration(String source) throws Exception {
        String path = root.toString().replace('\\', '/');
        String text = "SysOp=Test\nInbound="
                      + path
                      + "/in\nOutbound="
                      + path
                      + "/out\nTmp="
                      + path
                      + "/state\nEchoPath="
                      + path
                      + "/echo\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\nEchoSubscriptions=database\n"
                      + "MailScript=classpath:mail-scripts/areafix.groovy\n"
                      + "AreaFixPeer=1:500/101|robot-password|test.*|READ\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(("RobotAccessSource=" + source + "\n"
                + text).getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    @AfterEach
    void close() throws Exception {
        if (scripts != null) {
            scripts.close();
        }
        if (base != null) {
            base.close();
        }
    }

    private Message request(String sender, String password, String body, String id) {
        Message message = new Message("Sysop",
                                      "AreaFix",
                                      new FTNAddr(sender),
                                      new FTNAddr("1:500/100"),
                                      password,
                                      body,
                                      new Date());
        message.setMsgId(sender + " " + id);
        return message;
    }

    private void toss(Message message) throws Exception {
        Path inbox = Path.of(config.getInbound());
        Files.createDirectories(inbox);
        Path packet = inbox.resolve("request.pkt");
        Files.write(packet,
                    PacketWriter.write(peer.getAddr(),
                                       new FTNAddr("1:500/100"),
                                       "secret",
                                       List.of(message)));
        tosser.runFast(inbox.toString(), peer.getAddr());
        assertFalse(Files.exists(packet), "Request packet must be successfully processed");
    }
    private String reply() {
        return base.getQueuedNetmail()
                   .get(base.getQueuedNetmail().size() - 1)
                   .getMessage()
                   .getBody()
                   .getBody();
    }

    @Test
    void databaseAuthorizationAppliedLiveAndIgnoredFileCredentials() throws Exception {
        scripts.close();
        config = configuration("database");
        tosser = new Tosser(config, base);
        scripts = new MailAutomationRuntime(config, base, tosser);
        toss(request("1:500/101", "robot-password", "+test.one\r", "file-denied"));
        assertTrue(base.getQueuedNetmail().isEmpty());
        base.getRobotRules()
            .save(RobotAccessRule.create(RobotService.AreaFix,
                                         peer.getAddr().toString(),
                                         "db-password",
                                         "test.*",
                                         "READ",
                                         true));
        toss(request("1:500/101", "db-password", "+test.one\r", "db-allowed"));
        assertTrue(base.canReadEcho(peer, "test.one"));
        assertFalse(base.canWriteEcho(peer, "test.one"));
        base.getRobotRules().delete(RobotService.AreaFix, peer.getAddr());
        toss(request("1:500/101", "robot-password", "-test.one\r", "revoked-file"));
        toss(request("1:500/101", "db-password", "-test.one\r", "revoked-db"));
        assertTrue(base.canReadEcho(peer, "test.one"));
        assertEquals(1, base.getQueuedNetmail().size());
    }

    @Test
    void linksOnlyAllowedAreasListsQueriesAndDoesNotResubscribeOnDuplicateMsgid() throws Exception {
        Message request = request("1:500/101", "robot-password", "+*\r%LIST\r%QUERY\r", "one");
        toss(request);
        assertTrue(base.canReadEcho(peer, "test.one"));
        assertFalse(base.canWriteEcho(peer, "test.one"));
        assertFalse(base.canReadEcho(peer, "private"));
        assertTrue(reply().contains("test.one"));
        assertTrue(base.getQueuedNetmail()
                       .get(0)
                       .getMessage()
                       .getMsgId()
                       .matches(".* [0-9a-f]{8}"));
        assertFalse(reply().contains("private"));
        toss(request("1:500/101", "robot-password", "-test.one\r", "two"));
        assertFalse(base.canReadEcho(peer, "test.one"));
        scripts.close();
        scripts = new MailAutomationRuntime(config, base, tosser);
        request.setAttributes(1);
        toss(request);
        assertFalse(base.canReadEcho(peer, "test.one"));
        assertEquals(2, base.getQueuedNetmail().size());
    }

    @Test
    void refusesBadPasswordsAndForgedRoutedAuthorsAndReportsUnsupportedCommands() throws Exception {
        toss(request("1:500/101", "bad", "+test.one\r", "bad"));
        toss(request("1:500/999", "robot-password", "+test.one\r", "forged"));
        assertFalse(base.canReadEcho(peer, "test.one"));
        assertTrue(base.getQueuedNetmail().isEmpty());
        toss(request("1:500/101", "robot-password", "%PKTPWD surprise\r---\r+test.one", "unknown"));
        assertTrue(reply().contains("Unsupported command"));
        assertFalse(base.canReadEcho(peer, "test.one"));
    }

    @Test
    void rescansOnlyTheRequestedNumberOfMessages() throws Exception {
        tosser.configureEchoSubscription(peer.getAddr(),
                                         "test.one",
                                         AccessLevel.READ,
                                         EchoHistory.NEW_ONLY);
        for (int i = 0; i < 3; i++) {
            Message message = new Message("Writer",
                                          "All",
                                          new FTNAddr("1:500/100"),
                                          peer.getAddr(),
                                          "Subject " + i,
                                          "AREA:TEST.ONE\rHello\r",
                                          new Date());
            tosser.submitEchomail(message);
        }
        toss(request("1:500/101", "robot-password", "%RESCAN test.one 1\r", "rescan"));
        assertTrue(reply().contains("Rescan requested"));
        assertEquals(1, new OutboundSpool(config).pending(peer, false).size());
    }

    @Test
    void huskyQueriesSupportExclusionAndNeverEchoPasswordArguments() throws Exception {
        base.createArea("test.two");
        toss(request("1:500/101", "robot-password", "%LIST ! test.one\r", "exclude"));
        assertTrue(reply().contains("test.two"));
        assertFalse(reply().contains("test.one"));
        assertFalse(reply().contains("private"));
        toss(request("1:500/101", "robot-password", "%PKTPWD sensitive-value\r", "unsupported"));
        assertTrue(reply().contains("Unsupported command: %pktpwd"));
        assertFalse(reply().contains("sensitive-value"));
    }

    @Test
    void pausePersistsWithoutDeletingSubscriptionsOrPreparedEcho() throws Exception {
        tosser.configureEchoSubscription(peer.getAddr(),
                                         "test.one",
                                         AccessLevel.READ,
                                         EchoHistory.NEW_ONLY);
        Message echo = new Message("Writer",
                                   "All",
                                   new FTNAddr("1:500/100"),
                                   peer.getAddr(),
                                   "Subject",
                                   "AREA:TEST.ONE\rHello\r",
                                   new Date());
        tosser.submitEchomail(echo);
        tosser.exportEchomail();
        OutboundSpool spool = new OutboundSpool(config);
        Path packet = spool.pending(peer, false).get(0);
        toss(request("1:500/101", "robot-password", "%PAUSE\r%INFO\r", "pause"));
        assertTrue(reply().contains("paused"));
        assertTrue(base.canReadEcho(peer, "test.one"));
        assertTrue(Files.exists(packet));
        assertTrue(spool.pending(peer, false).isEmpty());
        scripts.close();
        scripts = new MailAutomationRuntime(config, base, tosser);
        assertTrue(spool.pending(peer, false).isEmpty());
        tosser.exportPendingNetmail();
        assertFalse(spool.pending(peer, false).isEmpty(),
                    "Robot netmail replies remain deliverable");
        assertFalse(spool.pending(peer, false).contains(packet));
        toss(request("1:500/101", "robot-password", "%RESUME\r", "resume"));
        assertTrue(spool.pending(peer, false).contains(packet));
    }

    @Test
    void huskySubscribeAndRescanSuffixWorksAndMalformedSuffixCannotSubscribe() throws Exception {
        for (int index = 0; index < 3; index++) {
            tosser.submitEchomail(new Message("Writer",
                                              "All",
                                              new FTNAddr("1:500/100"),
                                              peer.getAddr(),
                                              "Subject " + index,
                                              "AREA:TEST.ONE\rHello\r",
                                              new Date()));
        }
        toss(request("1:500/101", "robot-password", "+test.one /r=10001\r", "invalid-count"));
        assertFalse(base.canReadEcho(peer, "test.one"));
        toss(request("1:500/101", "robot-password", "+test.one /r=1\r", "subscribe-rescan"));
        assertTrue(base.canReadEcho(peer, "test.one"));
        assertEquals(1, new OutboundSpool(config).pending(peer, false).size());
    }
}
