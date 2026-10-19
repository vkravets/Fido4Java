package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.fts.FtsPkt;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DuplicateMailTest {
    @TempDir
    Path temporary;
    private JFtnConfig config;
    private MailDatabaseFacade base;
    private Link peer;

    @BeforeEach
    void open() throws Exception {
        config = config("");
        peer = config.getLink(new FTNAddr("1:500/101"));
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:duplicates" + System.nanoTime()));
        assertTrue(base.open());
        base.createArea("test");
        base.createArea("other");
        Files.createDirectories(Path.of(config.getInbound()));
    }

    @AfterEach
    void close() {
        base.close();
    }

    private JFtnConfig config(String extra) throws Exception {
        String root = temporary.toString().replace('\\', '/');
        String text = "Inbound="
                      + root
                      + "/in\nTmp="
                      + root
                      + "/tmp\nEchoPath="
                      + root
                      + "/echo\nOutbound="
                      + root
                      + "/out\nSysOp=Test\nEnforceEchoAccess=false\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\ndeletetossed=1\n"
                      + extra;
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private Message message(String area, String id, String text) {
        return new Message("Author",
                           "Reader",
                           peer.getAddr(),
                           new FTNAddr("1:500/100"),
                           "Subject",
                           (area == null ? "" : "AREA:" + area + "\r")
                           + (id == null ? "" : "\1MSGID: 1:500/101 " + id + "\r")
                           + text
                           + "\r",
                           new Date(1700000000000L));
    }

    private void deliver(Tosser tosser, Message message, int serial) throws Exception {
        byte[] bytes = PacketWriter.write(peer.getAddr(),
                                          peer.getMyaddr().get(0),
                                          "secret",
                                          Collections.singletonList(message));
        // Distinct packet delivery without changing message identity or relying on wall time.
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(16, (short) serial);
        Files.write(Path.of(config.getInbound(), serial + ".pkt"), bytes);
        tosser.runFast(config.getInbound());
    }

    private List<Path> duplicates() throws IOException {
        Path root = temporary.resolve("tmp/dupes");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(path -> path.toString().endsWith(".pkt"))
                        .collect(Collectors.toList());
        }
    }

    @Test
    void repeatedNetmailIsArchivedWithoutRepeatingRobotActions() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        Tosser tosser = new Tosser(config, base);
        tosser.addNetmailHandler((message, link, key) -> {
            requests.incrementAndGet();
            return true;
        });
        Message message = message(null, "request", "%LIST");
        deliver(tosser, message, 1);
        deliver(tosser, message, 2);
        deliver(new Tosser(config, base), message, 3);
        assertEquals(1, requests.get());
        assertEquals(1, base.getLocalNetmail(message.getTAddr()).size());
        assertEquals(1, duplicates().size());
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(duplicates().get(0))));
        Message stored = new Message(packet.getMsgs()[0],
                                     packet.getOrigZone(),
                                     packet.getDestZone());
        assertEquals(message.getMsgId(), stored.getMsgId());
        assertEquals(message.getBody().getBody(), stored.getBody().getBody());
        assertTrue(new OutboundSpool(config).pending(peer, true).isEmpty());
    }

    @Test
    void failedRobotIsRetriedForTheOriginalDeliveryAfterSqlSave() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Tosser first = new Tosser(config, base);
        first.addNetmailHandler((message, link, key) -> {
            attempts.incrementAndGet();
            throw new IOException("simulated robot failure");
        });
        Message message = message(null, "retry", "%LIST");
        deliver(first, message, 1);
        assertEquals(1, attempts.get());
        assertEquals(1, base.getLocalNetmail(message.getTAddr()).size());
        Tosser restarted = new Tosser(config, base);
        restarted.addNetmailHandler((mail, link, key) -> {
            attempts.incrementAndGet();
            return true;
        });
        restarted.runFast(config.getInbound());
        assertEquals(2, attempts.get());
        assertTrue(duplicates().isEmpty());
        deliver(restarted, message, 2);
        assertEquals(2, attempts.get());
        assertEquals(1, duplicates().size());
    }

    @Test
    void echoDuplicatesAreScopedToAreaAndDifferentDuplicateBodiesArePreserved() throws Exception {
        Tosser tosser = new Tosser(config, base);
        deliver(tosser, message("test", "same", "Original"), 1);
        deliver(tosser, message("other", "same", "Other area"), 2);
        deliver(tosser, message("test", "same", "Duplicate one"), 3);
        deliver(tosser, message("test", "same", "Duplicate two"), 4);
        assertEquals(1, base.getMessageSize("test"));
        assertEquals(1, base.getMessageSize("other"));
        assertEquals(2, duplicates().size());
        for (Path packet : duplicates()) {
            assertTrue(packet.getFileName().toString().matches("[0-9a-f]{8}\\.pkt"));
        }
    }

    @Test
    void messagesWithoutMsgidUseContentAndDiscardIsExplicit() throws Exception {
        Tosser tosser = new Tosser(config, base);
        Message first = message(null, null, "Legacy");
        deliver(tosser, first, 1);
        deliver(tosser, first, 2);
        deliver(tosser, message(null, null, "Different"), 3);
        assertEquals(2, base.getLocalNetmail(first.getTAddr()).size());
        assertEquals(1, duplicates().size());
        JFtnConfig discard = config("DupeAction=discard\n");
        assertEquals(null, new DuplicateMailArchive(discard).retain(first, peer));
        assertEquals(1, duplicates().size());
        assertThrows(IllegalArgumentException.class,
                     () -> new Tosser(config("DupeAction=off\n"), base));
    }

    @Test
    void archiveWriteFailureDoesNotCompleteThePacketJournal() throws Exception {
        Tosser tosser = new Tosser(config, base);
        Message message = message(null, "failure", "Body");
        deliver(tosser, message, 1);
        Path blocked = temporary.resolve("tmp/dupes");
        Files.writeString(blocked, "not a directory");
        deliver(tosser, message, 2);
        assertTrue(Files.exists(Path.of(config.getInbound(), "2.pkt")));
        Files.delete(blocked);
        new Tosser(config, base).runFast(config.getInbound());
        assertEquals(1, duplicates().size());
        assertFalse(Files.exists(Path.of(config.getInbound(), "2.pkt")));
    }
}
