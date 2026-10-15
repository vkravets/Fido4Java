package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.fts.FtsPkt;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

public class EchoPolicyTest {
    @TempDir
    public File temporary;
    private DatabaseManager base;
    private JFtnConfig config;
    private Tosser tosser;
    private OutboundSpool spool;
    private String url;

    private JFtnConfig settings(String extra) throws Exception {
        String root = temporary.getPath().replace('\\', '/');
        String text = "Inbound="
                      + root
                      + "/in\nOutbound="
                      + root
                      + "/out\nTmp="
                      + root
                      + "/state\nEchoPath="
                      + root
                      + "/echo\nSysOp=Test\nEchoSubscriptions=database\n"
                      + "Link=1:500/101,1:500/102 1:500/202,secret,localhost\n"
                      + "Link=1:500/103,1:500/102,secret,localhost\n"
                      + "Link=1:500/104,1:500/102,secret,localhost\n"
                      + "Link=1:500/101.1,1:500/102,secret,localhost\n"
                      + extra;
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    @BeforeEach
    public void open() throws Exception {
        config = settings("");
        url = "jdbc:h2:mem:echoPolicy" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        base = new DatabaseManager(new OrmManager(url));
        assertTrue(base.open());
        for (Link link : config.getConfiguredLinks()) {
            base.configureLink(link, "secret");
        }
        assertTrue(base.createArea("test"));
        tosser = new Tosser(config, base);
        spool = new OutboundSpool(config);
    }

    @AfterEach
    public void close() {
        base.close();
    }

    private FTNAddr peer(String node) {
        return new FTNAddr("1:500/" + node);
    }

    private Link link(String node) {
        return config.getLink(peer(node));
    }

    private Message message(int id) {
        return new Message("Author",
                           "All",
                           peer("102"),
                           peer("101"),
                           "Subject",
                           "AREA:TEST\r\1MSGID: 1:500/102 echo-" + id + "\rHello\r",
                           new Date(1700000000000L));
    }

    private void subscribe(String node, AccessLevel access, EchoHistory history) {
        tosser.configureEchoSubscription(peer(node), "test", access, history);
    }

    private List<Path> pending(String node) throws Exception {
        return spool.pending(link(node), true);
    }

    private void acknowledge(String node) throws Exception {
        for (Path packet : pending(node)) {
            spool.acknowledge(packet);
        }
    }

    @Test
    public void explainsSourceSeenByPermissionsAndPointDeliveryWithoutWritingPackets()
            throws Exception {
        subscribe("101", AccessLevel.BOTH, EchoHistory.ALL);
        subscribe("103", AccessLevel.READ, EchoHistory.ALL);
        subscribe("104", AccessLevel.WRITE, EchoHistory.ALL);
        subscribe("101.1", AccessLevel.READ, EchoHistory.ALL);
        Message mail = message(1);
        mail.setUpLink(peer("101"));
        mail.getBody().getSeenBy().add(peer("103"));
        mail.getBody().getSeenBy().add(peer("101"));
        List<EchoDecision> decisions = tosser.getEchoRouter().explain(mail);
        assertEquals(EchoDecision.Reason.INPUT_LINK, reason(decisions, "101"));
        assertEquals(EchoDecision.Reason.SEEN_BY, reason(decisions, "103"));
        assertEquals(EchoDecision.Reason.NO_READ_ACCESS, reason(decisions, "104"));
        assertEquals(EchoDecision.Reason.ELIGIBLE, reason(decisions, "101.1"));
        for (Link link : config.getConfiguredLinks()) {
            assertTrue(spool.pending(link, true).isEmpty());
        }
    }

    private EchoDecision.Reason reason(List<EchoDecision> decisions, String node) {
        return decisions.stream()
                        .filter(decision -> decision.getLink().getAddr().equals(peer(node)))
                        .findFirst()
                        .get()
                        .getReason();
    }

    @Test
    public void newOnlySkipsHistoryAndPreservesPositionsAcrossRemovalAndRestart() throws Exception {
        for (int id = 0; id < 3; id++) {
            base.addMessage(message(id), "test");
        }
        subscribe("101", AccessLevel.READ, EchoHistory.NEW_ONLY);
        tosser.exportEchomail();
        assertTrue(pending("101").isEmpty());
        base.addMessage(message(3), "test");
        tosser.exportEchomail();
        assertEquals(1, pending("101").size());
        acknowledge("101");
        tosser.removeEchoSubscription(peer("101"), "test");
        tosser.removeEchoSubscription(peer("101"), "test");
        base.addMessage(message(4), "test");
        tosser.exportEchomail();
        assertTrue(pending("101").isEmpty());
        subscribe("101", AccessLevel.READ, EchoHistory.NEW_ONLY);
        tosser.exportEchomail();
        assertEquals(1, pending("101").size());
        acknowledge("101");
        base.close();
        base = new DatabaseManager(new OrmManager(url));
        assertTrue(base.open());
        tosser = new Tosser(config, base);
        assertTrue(base.canReadEcho(link("101"), "test"));
        tosser.exportEchomail();
        assertTrue(pending("101").isEmpty());
        base.addMessage(message(5), "test");
        tosser.exportEchomail();
        assertEquals(1, pending("101").size());
    }

    @Test
    public void accessChangesAndUnsubscribeKeepAlreadyPreparedPackets() throws Exception {
        base.addMessage(message(1), "test");
        subscribe("101", AccessLevel.READ, EchoHistory.ALL);
        tosser.exportEchomail();
        assertEquals(1, pending("101").size());
        subscribe("101", AccessLevel.WRITE, EchoHistory.ALL);
        assertFalse(base.canReadEcho(link("101"), "test"));
        assertTrue(base.canWriteEcho(link("101"), "test"));
        base.addMessage(message(2), "test");
        tosser.exportEchomail();
        assertEquals(1, pending("101").size());
        tosser.removeEchoSubscription(peer("101"), "test");
        assertFalse(base.canWriteEcho(link("101"), "test"));
        assertEquals(1, pending("101").size());
    }

    @Test
    public void explicitRescanIsBoundedIdempotentAndDoesNotResetNormalDelivery() throws Exception {
        for (int id = 0; id < 3; id++) {
            base.addMessage(message(id), "test");
        }
        subscribe("101", AccessLevel.READ, EchoHistory.ALL);
        tosser.exportEchomail();
        assertEquals(3, pending("101").size());
        acknowledge("101");
        tosser.rescanEcho("test", peer("101"), 2, 2, "history-1");
        tosser.rescanEcho("test", peer("101"), 2, 2, "history-1");
        assertEquals(1, pending("101").size());
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(pending("101").get(0))));
        Message copy = new Message(packet.getMsgs()[0], packet.getOrigZone(), packet.getDestZone());
        assertEquals(message(1).getMsgId(), copy.getMsgId());
        acknowledge("101");
        tosser.rescanEcho("test", peer("101"), 2, 2, "history-1");
        assertTrue(pending("101").isEmpty());
        tosser.exportEchomail();
        assertTrue(pending("101").isEmpty());
        tosser.rescanEcho("test", peer("101"), 2, 2, "history-2");
        assertEquals(1, pending("101").size());
        try {
            tosser.rescanEcho("test", peer("104"), 0, 10, "denied");
            fail("Unreadable rescan accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(pending("104").isEmpty());
        }
    }

    @Test
    public void selectedAkaIsUsedInEnvelopeOriginAndRoutingLines() throws Exception {
        config = settings("EchoAka=TEST,1:500/101,1:500/202\n");
        tosser = new Tosser(config, base);
        subscribe("101", AccessLevel.READ, EchoHistory.ALL);
        Message original = message(1);
        tosser.submitEchomail(original);
        tosser.exportEchomail();
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(pending("101").get(0))));
        assertEquals(peer("202"), packet.getOrigaddr());
        Message copy = new Message(packet.getMsgs()[0], packet.getOrigZone(), packet.getDestZone());
        assertTrue(copy.getBody().getOrigin().contains("1:500/202"));
        assertTrue(copy.getBody().getSeenBy().getAddresses().contains(peer("202")));
        assertTrue(copy.getBody().getPath().getAddresses().contains(peer("202")));
        assertNull(original.getBody().getOrigin());
    }

    @Test
    public void rescanCanReplaySourceSeenMailButNeverRedistributesArchives() throws Exception {
        Message received = message(1);
        received.setUpLink(peer("101"));
        received.getBody().getSeenBy().add(peer("101"));
        base.addMessage(received, "test");
        Message archived = message(2);
        assertTrue(base.importArchive(archived, "historical-jam"));
        subscribe("101", AccessLevel.READ, EchoHistory.ALL);
        tosser.exportEchomail();
        assertTrue(pending("101").isEmpty());
        tosser.rescanEcho("test", peer("101"), 0, Long.MAX_VALUE, "explicit-history");
        assertEquals(1, pending("101").size());
        assertEquals(EchoDecision.Reason.ARCHIVED,
                     reason(tosser.getEchoRouter().explain(archived), "101"));
        try {
            tosser.rescanEcho("test", peer("101"), 10, 1, "bad-range");
            fail("Invalid range accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(1, pending("101").size());
        }
    }

    @Test
    public void rejectsForeignAndDuplicateAliasBindingsAtStartup() throws Exception {
        for (String extra : new String[]{"EchoAka=TEST,1:500/101,1:500/999\n",
                "EchoAka=TEST,1:500/101,1:500/202\nEchoAka=test,1:500/101,1:500/102\n"}) {
            try {
                new Tosser(settings(extra), base);
                fail("Invalid AKA configuration accepted");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().length() > 0);
            }
        }
    }
}
