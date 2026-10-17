package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class QuarantineRecoveryTest {
    @TempDir
    Path root;
    private JFtnConfig config;
    private DatabaseManager base;
    private Tosser tosser;
    private final FTNAddr peer = new FTNAddr("1:500/101");
    private final FTNAddr local = new FTNAddr("1:500/100");

    @BeforeEach
    void open() throws Exception {
        Files.createDirectories(root.resolve("in"));
        Files.createDirectories(root.resolve("state"));
        String path = root.toString().replace('\\', '/');
        String text = "Inbound="
                      + path
                      + "/in\nOutbound="
                      + path
                      + "/out\nTmp="
                      + path
                      + "/state\nEchoPath="
                      + path
                      + "/echo\nSysOp=Test\nDeletetossed=1\nEchoSubscriptions=database\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\n"
                      + "Link=1:500/103,1:500/100,secret,localhost\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:quarantine" + System.nanoTime()));
        assertTrue(base.open());
        base.configureLink(config.getLink(peer), "secret");
        base.configureLink(config.getLink(new FTNAddr("1:500/103")), "secret");
        tosser = new Tosser(config, base);
    }

    @AfterEach
    void close() {
        if (base != null)
            base.close();
    }

    private byte[] packet(String password, String... texts) throws IOException {
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        for (String text : texts) {
            ByteBuffer header = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
            for (int number : new int[]{2, 101, 100, 500, 500, 0, 0}) {
                header.putShort((short) number);
            }
            header.put("12 Oct 26  12:34:56".getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
            messages.write(header.array());
            for (String field : new String[]{"Reader", "Author", "Subject", text}) {
                messages.write(field.getBytes(StandardCharsets.US_ASCII));
                messages.write(0);
            }
        }
        ByteBuffer packet = ByteBuffer.allocate(60 + messages.size())
                                      .order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort(0, (short) 101);
        packet.putShort(2, (short) 100);
        packet.putShort(18, (short) 2);
        packet.putShort(20, (short) 500);
        packet.putShort(22, (short) 500);
        packet.putShort(34, (short) 1);
        packet.putShort(36, (short) 1);
        packet.position(26);
        packet.put(password.getBytes(StandardCharsets.US_ASCII));
        packet.position(58);
        packet.put(messages.toByteArray()).putShort((short) 0);
        return packet.array();
    }

    private PacketQuarantine.Item reject(byte[] bytes) throws Exception {
        Files.write(root.resolve("in/message.pkt"), bytes);
        tosser.runFast(root.resolve("in").toString(), peer);
        return tosser.quarantine("", 50).items.get(0);
    }

    @Test
    void resumesPartialPacketAfterPermissionFixAndRestartWithoutDuplicatingAcceptedMessages()
            throws Exception {
        byte[] bytes = packet("secret",
                              "\1MSGID: 1:500/101 accepted\rNetmail\r",
                              "AREA:NEW.ECHO\r\1MSGID: 1:500/101 new-echo\rEcho\r");
        PacketQuarantine.Item item = reject(bytes);
        assertEquals(1, base.getLocalNetmail(local).size());
        assertTrue(item.reason.contains("Unknown echo area"));
        assertEquals(peer.toString(), item.peer);
        tosser.configureEchoSubscription(peer, "new.echo", AccessLevel.WRITE, EchoHistory.NEW_ONLY);
        tosser = new Tosser(config, base);
        assertTrue(tosser.retryQuarantine(item.id, item.checksum, peer).accepted);
        assertEquals(1, base.getLocalNetmail(local).size());
        assertEquals(1, base.getMessageSize("new.echo"));
        assertTrue(tosser.quarantine("", 50).items.isEmpty());
        assertArrayEquals(bytes,
                          Files.readAllBytes(root.resolve("state/.quarantine/archive/"
                                                          + item.checksum
                                                          + ".bad")));
        Files.write(root.resolve("state/replay.bad"), bytes);
        PacketQuarantine.Item replay = tosser.quarantine("", 50).items.get(0);
        assertThrows(IllegalArgumentException.class,
                     () -> tosser.retryQuarantine(replay.id, replay.checksum, null));
        assertTrue(tosser.retryQuarantine(replay.id, replay.checksum, peer).accepted);
        assertEquals(1, base.getLocalNetmail(local).size());
        assertEquals(1, base.getMessageSize("new.echo"));
    }

    @Test
    void retainsWrongPasswordPacketAndOriginalSessionBindingOnRetry() throws Exception {
        byte[] bytes = packet("wrong", "\1MSGID: 1:500/101 denied\rHello\r");
        PacketQuarantine.Item item = reject(bytes);
        assertTrue(item.reason.contains("PKT password mismatch"));
        assertFalse(item.reason.contains("wrong"));
        assertFalse(item.reason.contains("secret"));
        assertThrows(IllegalArgumentException.class,
                     () -> tosser.retryQuarantine(item.id,
                                                  item.checksum,
                                                  new FTNAddr("1:500/103")));
        assertFalse(tosser.retryQuarantine(item.id, item.checksum, peer).accepted);
        PacketQuarantine.Item retained = tosser.quarantine("", 50).items.get(0);
        assertEquals(1, retained.attempts);
        assertTrue(retained.lastAttempt > 0);
        assertEquals(peer.toString(), retained.peer);
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("state/message.bad")));
        assertTrue(base.getLocalNetmail(local).isEmpty());
        config.load(new ByteArrayInputStream("PacketPassword=1:500/101,wrong\n".getBytes(StandardCharsets.UTF_8)));
        tosser = new Tosser(config, base);
        assertTrue(tosser.retryQuarantine(item.id, item.checksum, peer).accepted);
        assertEquals(1, base.getLocalNetmail(local).size());
        assertTrue(tosser.quarantine("", 50).items.isEmpty());
    }

    @Test
    void malformedPacketRetryRetainsActiveBytesAndRecordsFailedAttempt() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        Files.write(root.resolve("state/malformed.bad"), bytes);
        PacketQuarantine.Item item = tosser.quarantine("", 25).items.get(0);
        assertThrows(IOException.class, () -> tosser.retryQuarantine(item.id, item.checksum, peer));
        PacketQuarantine.Item retained = tosser.quarantine("", 25).items.get(0);
        assertEquals(1, retained.attempts);
        assertTrue(retained.reason.contains("processing failed"));
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("state/malformed.bad")));
    }

    @Test
    void rejectsStaleSnapshotsAndArchivesLegacyPacketsWithoutImportingThem() throws Exception {
        byte[] bytes = packet("secret", "\1MSGID: 1:500/101 legacy\rHello\r");
        Path path = root.resolve("state/legacy.bad");
        Files.write(path, bytes);
        PacketQuarantine.Item item = tosser.quarantine("", 50).items.get(0);
        assertEquals("", item.peer);
        byte[] changed = bytes.clone();
        changed[8] ^= 1;
        Files.write(path, changed);
        assertThrows(IllegalArgumentException.class,
                     () -> tosser.archiveQuarantine(item.id, item.checksum));
        assertArrayEquals(changed, Files.readAllBytes(path));
        PacketQuarantine.Item current = tosser.quarantine("", 50).items.get(0);
        tosser.archiveQuarantine(current.id, current.checksum);
        assertFalse(Files.exists(path));
        assertArrayEquals(changed,
                          Files.readAllBytes(root.resolve("state/.quarantine/archive/"
                                                          + current.checksum
                                                          + ".bad")));
        assertTrue(base.getLocalNetmail(local).isEmpty());
        assertThrows(IllegalArgumentException.class,
                     () -> tosser.archiveQuarantine("../outside.bad", current.checksum));
        assertThrows(IllegalArgumentException.class, () -> tosser.quarantine("../outside", 50));
        assertThrows(IllegalArgumentException.class, () -> tosser.quarantine("", 201));
    }

    @Test
    void preservesActivePacketWhenItsRecoveryCopyIsDamaged() throws Exception {
        byte[] bytes = packet("secret");
        Files.write(root.resolve("state/legacy.bad"), bytes);
        PacketQuarantine.Item item = tosser.quarantine("", 50).items.get(0);
        Path archive = Files.createDirectories(root.resolve("state/.quarantine/archive"));
        Files.write(archive.resolve(item.checksum + ".bad"), new byte[]{7});
        assertThrows(IOException.class, () -> tosser.archiveQuarantine(item.id, item.checksum));
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("state/legacy.bad")));
    }

    @Test
    void pagesLegacyPacketsAndDoesNotExposeDamagedMetadataAsTrustedPeerInformation()
            throws Exception {
        Files.write(root.resolve("state/a.bad"), new byte[]{1});
        Files.write(root.resolve("state/b.bad"), new byte[]{2});
        PacketQuarantine.Page first = tosser.quarantine("", 1);
        assertTrue(first.hasMore);
        assertEquals("a.bad", first.items.get(0).name);
        PacketQuarantine.Page second = tosser.quarantine(first.nextCursor, 1);
        assertFalse(second.hasMore);
        assertEquals("b.bad", second.items.get(0).name);
        Path metadata = root.resolve("state/.quarantine/metadata/"
                                     + first.items.get(0).id
                                     + ".properties");
        Files.writeString(metadata, "checksum=invalid\npeer=1:500/103.0\n");
        PacketQuarantine.Item damaged = tosser.quarantine("", 50).items.get(0);
        assertEquals("", damaged.checksum);
        assertEquals("", damaged.peer);
        assertTrue(damaged.reason.contains("metadata is unavailable"));
    }
}
