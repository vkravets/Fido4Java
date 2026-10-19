package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.fts.FtsPkt;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.UUID;

class OutboundPacketNameTest {
    @TempDir
    Path temporary;
    private JFtnConfig config;
    private Link link;

    @BeforeEach
    void configure() throws Exception {
        config = new JFtnConfig();
        String text = "Outbound="
                      + temporary.toString()
                                 .replace('\\', '/')
                      + "\nInbound="
                      + temporary.resolve("in")
                                 .toString()
                                 .replace('\\', '/')
                      + "\nTmp="
                      + temporary.resolve("tmp")
                                 .toString()
                                 .replace('\\', '/')
                      + "\nEchoPath="
                      + temporary.resolve("echo")
                                 .toString()
                                 .replace('\\', '/')
                      + "\nSysOp=Test\nLink=1:500/101,1:500/100,secret,localhost\n";
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        link = config.getLink(new FTNAddr("1:500/101"));
    }

    private Message message() {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("1:500/100"),
                           new FTNAddr("1:500/101"),
                           "Subject",
                           "Hello\r",
                           new Date());
    }

    private Path directory() {
        return temporary.resolve("jftn-mail/1-500-101-0");
    }

    private String identity(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Test
    void netmailUsesShortStableNamesAndAcknowledgesAfterRestart() throws Exception {
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("netmail:first", link, message());
        Path packet = spool.pending(link, true).get(0);
        assertTrue(packet.getFileName().toString().matches("[0-9a-f]{8}\\.pkt"));
        byte[] original = Files.readAllBytes(packet);
        assertEquals(101, new FtsPkt(ByteBuffer.wrap(original)).getDestNode());
        spool = new OutboundSpool(config);
        spool.enqueue("netmail:first", link, message());
        assertEquals(List.of(packet), spool.pending(link, true));
        assertArrayEquals(original, Files.readAllBytes(packet));
        assertEquals("netmail:first", spool.acknowledge(packet));
        assertEquals("netmail:first", spool.acknowledge(packet));
        spool.enqueue("netmail:first", link, message());
        assertTrue(spool.pending(link, true).isEmpty());
        assertTrue(spool.isAcknowledged("netmail:first", link));
        assertFalse(spool.isAcknowledged("netmail:other", link));
    }

    @Test
    void resumesInterruptedPublicationUsingRecordedName() throws Exception {
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("netmail:first", link, message());
        Path packet = spool.pending(link, true).get(0);
        Files.delete(packet);
        Files.delete(packet.resolveSibling(packet.getFileName() + ".owner"));
        assertTrue(new OutboundSpool(config).pending(link, true).isEmpty());
        new OutboundSpool(config).enqueue("netmail:first", link, message());
        assertEquals(List.of(packet), spool.pending(link, true));
    }

    @Test
    void retainsPreviouslyQueuedLongNamesAndReceipts() throws Exception {
        Files.createDirectories(directory());
        String stem = identity("netmail:legacy");
        Path packet = directory().resolve(stem + ".pkt");
        byte[] bytes = {1, 2, 3};
        Files.write(packet, bytes);
        Files.writeString(directory().resolve(stem + ".properties"), "key=netmail:legacy\n");
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("netmail:legacy", link, message());
        assertEquals(List.of(packet), spool.pending(link, true));
        assertArrayEquals(bytes, Files.readAllBytes(packet));
        spool.acknowledge(packet);
        assertTrue(new OutboundSpool(config).isAcknowledged("netmail:legacy", link));
    }

    @Test
    void rejectsCorruptSequenceAndUnsafeMetadata() throws Exception {
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("netmail:first", link, message());
        Files.writeString(temporary.resolve("jftn-mail/packet.sequence"), "broken");
        assertThrows(IOException.class, () -> spool.enqueue("other", link, message()));
        Files.writeString(directory().resolve(identity("netmail:first") + ".properties"),
                          "key=netmail:first\npacket=../outside.pkt\n");
        assertThrows(IOException.class, () -> spool.pending(link, true));
    }
}
