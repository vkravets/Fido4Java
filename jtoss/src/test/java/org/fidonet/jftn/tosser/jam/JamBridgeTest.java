package org.fidonet.jftn.tosser.jam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class JamBridgeTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private DatabaseManager base;
    private JFtnConfig config;
    private JamBridge bridge;
    private JamArea echo;
    private JamArea netmail;
    private Path root;

    @Before
    public void open() throws Exception {
        root = temporary.getRoot().toPath();
        String directory = root.toString().replace('\\', '/');
        String text = "Inbound="
                      + directory
                      + "/inbound\nTmp="
                      + directory
                      + "/state\n"
                      + "Outbound="
                      + directory
                      + "/outbound\nEchoPath="
                      + directory
                      + "/echo\nSysOp=Test\n"
                      + "Link=2:500/101,2:500/102,secret,localhost\n"
                      + "Link=2:500/103,2:500/102,secret,localhost\nDefaultRoute=2:500/101\n"
                      + "JamEcho=TEST,"
                      + directory
                      + "/jam/test\nJamNetmail="
                      + directory
                      + "/jam/netmail\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:jam" + System.nanoTime()));
        assertTrue(base.open());
        assertTrue(base.createArea("test"));
        base.configureLink(config.getLink(new FTNAddr("2:500/101")), "secret");
        base.configureSubscription(config.getLink(new FTNAddr("2:500/101")),
                                   "test",
                                   AccessLevel.BOTH);
        bridge = new JamBridge(config, base, base);
        echo = new JamArea(root.resolve("jam/test"), "test", new FTNAddr("2:500/102"));
        netmail = new JamArea(root.resolve("jam/netmail"), null, new FTNAddr("2:500/102"));
    }

    @After
    public void close() {
        base.close();
    }

    private Message message(boolean echomail) {
        return new Message("Remote",
                           "Sysop",
                           new FTNAddr("2:500/101"),
                           new FTNAddr("2:500/102"),
                           "Тема",
                           (echomail ? "AREA:TEST\r" : "")
                           + "\1MSGID: 2:500/101 abc\r\1CHRS: UTF-8 4\rПривет\r",
                           new Date(1700000000000L));
    }

    @Test
    public void exportsEchoAndLocalNetmailAcrossRestartWithoutDuplicates() throws Exception {
        base.addMessage(message(true), "test");
        base.saveNetmail(message(false), "inbound", null);
        bridge.exportMessages();
        new JamBridge(config, base, base).exportMessages();
        assertEquals(1, echo.read().size());
        assertEquals(1, netmail.read().size());
        assertEquals("Тема", echo.read().get(0).getMessage().getSubject());
        assertFalse((echo.read().get(0).getAttributes() & JamArea.LOCAL) != 0);
    }

    @Test
    public void preservesLastReadsAndDoesNotResurrectEditorDeletedMessages() throws Exception {
        base.addMessage(message(true), "test");
        bridge.exportMessages();
        Path lastread = Path.of(echo.getPath() + ".jlr");
        Files.write(lastread, new byte[16]);
        echo.locked(access -> {
            access.attributes(1, JamArea.DELETED, 0);
            return null;
        });
        bridge.exportMessages();
        assertTrue(echo.read().isEmpty());
        assertEquals(16, Files.size(lastread));
        assertEquals(1, base.getMessageSize("test"));
    }

    private Message local(boolean echomail, boolean withId) {
        return new Message("Sysop",
                           "Remote",
                           new FTNAddr("2:500/102"),
                           new FTNAddr("2:500/101"),
                           "Outgoing",
                           (echomail ? "AREA:TEST\r" : "")
                           + (withId ? "\1MSGID: 2:500/102 local\r" : "")
                           + "Hello\r",
                           new Date(1700000000000L));
    }

    @Test
    public void importsLocalPostsOnceAndPersistsMissingMsgidInJam() throws Exception {
        bridge.exportMessages();
        echo.append(local(true, false), JamArea.ECHO | JamArea.LOCAL);
        netmail.append(local(false, true), JamArea.NET | JamArea.LOCAL);
        Tosser tosser = new Tosser(config, base);
        bridge.importOutgoing(tosser);
        new JamBridge(config, base, base).importOutgoing(tosser);
        assertEquals(1, base.getMessageSize("test"));
        assertEquals(1, base.getQueuedNetmail().size());
        assertTrue(echo.read()
                       .get(0)
                       .getMessage()
                       .getMsgId()
                       .startsWith(new FTNAddr("2:500/102") + " "));
        assertTrue((echo.read().get(0).getAttributes() & JamArea.LOCKED) != 0);
        assertFalse((echo.read().get(0).getAttributes() & JamArea.SENT) != 0);
    }

    @Test
    public void rejectsForeignSenderAndPreviouslyStoredMsgid() throws Exception {
        base.addMessage(message(true), "test");
        bridge.exportMessages();
        echo.append(message(true), JamArea.ECHO | JamArea.LOCAL);
        Message duplicate = local(true, true);
        base.addMessage(duplicate, "test");
        echo.append(duplicate, JamArea.ECHO | JamArea.LOCAL);
        bridge.importOutgoing(new Tosser(config, base));
        assertEquals(2, base.getMessageSize("test"));
        assertEquals(0, echo.read().get(1).getAttributes() & JamArea.LOCKED);
        assertEquals(0, echo.read().get(2).getAttributes() & JamArea.LOCKED);
    }

    @Test
    public void recoversWhenSqlCommittedBeforeJamReceiptWasSaved() throws Exception {
        bridge.exportMessages();
        echo.append(local(true, true), JamArea.ECHO | JamArea.LOCAL);
        Tosser interrupted = new Tosser(config, base) {
            @Override
            public synchronized void submitEchomail(Message message) throws IOException {
                super.submitEchomail(message);
                throw new IOException("Simulated interruption after SQL commit");
            }
        };
        try {
            bridge.importOutgoing(interrupted);
            throw new AssertionError("Expected interruption");
        } catch (IOException expected) {
            assertEquals(1, base.getMessageSize("test"));
        }
        new JamBridge(config, base, base).importOutgoing(new Tosser(config, base));
        assertEquals(1, base.getMessageSize("test"));
        assertTrue((echo.read().get(0).getAttributes() & JamArea.LOCKED) != 0);
    }

    @Test
    public void marksSentOnlyAfterReceiptAndRecoversFlagsAfterRestart() throws Exception {
        bridge.exportMessages();
        netmail.append(local(false, true), JamArea.NET | JamArea.LOCAL);
        Tosser tosser = new Tosser(config, base);
        bridge.importOutgoing(tosser);
        tosser.exportPendingNetmail();
        bridge.reconcileDeliveries();
        assertEquals(0, netmail.read().get(0).getAttributes() & JamArea.SENT);
        OutboundSpool spool = new OutboundSpool(config);
        spool.acknowledge(spool.pending(config.getLink(new FTNAddr("2:500/101")), true).get(0));
        new JamBridge(config, base, base).reconcileDeliveries();
        assertTrue((netmail.read().get(0).getAttributes() & JamArea.SENT) != 0);
        assertEquals(0, netmail.read().get(0).getAttributes() & JamArea.LOCKED);
    }

    @Test
    public void waitsForAllEchoRecipients() throws Exception {
        base.configureLink(config.getLink(new FTNAddr("2:500/103")), "secret");
        base.configureSubscription(config.getLink(new FTNAddr("2:500/103")),
                                   "test",
                                   AccessLevel.BOTH);
        bridge.exportMessages();
        echo.append(local(true, true), JamArea.ECHO | JamArea.LOCAL);
        Tosser tosser = new Tosser(config, base);
        bridge.importOutgoing(tosser);
        tosser.exportEchomail();
        OutboundSpool spool = new OutboundSpool(config);
        spool.acknowledge(spool.pending(config.getLink(new FTNAddr("2:500/101")), true).get(0));
        bridge.reconcileDeliveries();
        assertEquals(0, echo.read().get(0).getAttributes() & JamArea.SENT);
        spool.acknowledge(spool.pending(config.getLink(new FTNAddr("2:500/103")), true).get(0));
        bridge.reconcileDeliveries();
        assertTrue((echo.read().get(0).getAttributes() & JamArea.SENT) != 0);
    }

    @Test
    public void detectsExternalEditsOfQueuedContentWithoutSendingAnotherVersion() throws Exception {
        bridge.exportMessages();
        netmail.append(local(false, true), JamArea.NET | JamArea.LOCAL);
        Tosser tosser = new Tosser(config, base);
        bridge.importOutgoing(tosser);
        tosser.exportPendingNetmail();
        Path text = Path.of(netmail.getPath() + ".jdt");
        Files.writeString(text, "Other\r", StandardCharsets.US_ASCII);
        OutboundSpool spool = new OutboundSpool(config);
        spool.acknowledge(spool.pending(config.getLink(new FTNAddr("2:500/101")), true).get(0));
        bridge.reconcileDeliveries();
        bridge.importOutgoing(tosser);
        assertEquals(0, netmail.read().get(0).getAttributes() & JamArea.SENT);
        assertEquals(1, base.getQueuedNetmail().size());
    }
}
