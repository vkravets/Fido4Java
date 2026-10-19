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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    void echoFanOutAndRescansHaveIndependentShortPacketNames() throws Exception {
        Link other = new Link(new FTNAddr("1:500/102"),
                              link.getMyaddr(),
                              "secret",
                              "localhost",
                              24554);
        Message echo = new Message("Author",
                                   "All",
                                   new FTNAddr("1:500/100"),
                                   link.getAddr(),
                                   "Subject",
                                   "AREA:TEST\rHello\r",
                                   new Date());
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("echo:first", link, echo);
        spool.enqueue("echo:first", other, echo);
        Path first = spool.pending(link, true).get(0);
        Path second = spool.pending(other, true).get(0);
        assertTrue(first.getFileName().toString().matches("[0-9a-f]{8}\\.pkt"));
        assertTrue(second.getFileName().toString().matches("[0-9a-f]{8}\\.pkt"));
        assertFalse(first.getFileName().equals(second.getFileName()));
        spool.acknowledge(first);
        spool = new OutboundSpool(config);
        spool.enqueue("echo:first", link, echo);
        assertTrue(spool.pending(link, true).isEmpty());
        assertEquals(List.of(second), spool.pending(other, true));
        spool.enqueue("rescan:requested:echo:first", link, echo);
        Path rescan = spool.pending(link, true).get(0);
        assertFalse(first.getFileName().equals(rescan.getFileName()));
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(rescan)));
        Message restored = new Message(packet.getMsgs()[0],
                                       packet.getOrigZone(),
                                       packet.getDestZone());
        assertEquals("TEST", restored.getArea());
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

    @Test
    void skipsOccupiedPacketNamesAndRetainedOwnerReservations() throws Exception {
        Files.createDirectories(directory());
        long serial = System.currentTimeMillis() / 1000 + 3600;
        Path sequence = temporary.resolve("jftn-mail/packet.sequence");
        Files.writeString(sequence, Long.toString(serial - 1));
        Path occupied = directory().resolve(String.format(Locale.ROOT, "%08x.pkt", serial));
        Files.write(occupied, new byte[]{1, 2, 3});
        Files.writeString(directory().resolve(String.format(Locale.ROOT,
                                                            "%08x.pkt.owner",
                                                            serial + 1)),
                          "reserved");
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("netmail:collision", link, message());
        assertEquals(String.format(Locale.ROOT, "%08x.pkt", serial + 2),
                     spool.pending(link, true).get(0).getFileName().toString());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(occupied));
    }

    @Test
    void multipleSpoolInstancesPublishOnlyOnePacketPerDelivery() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> work = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                String key = "netmail:parallel:" + index % 4;
                work.add(executor.submit(() -> {
                    new OutboundSpool(config).enqueue(key, link, message());
                    return null;
                }));
            }
            for (Future<?> result : work) {
                result.get();
            }
            List<Path> packets = new OutboundSpool(config).pending(link, true);
            assertEquals(4, packets.size());
            assertEquals(4, packets.stream().map(Path::getFileName).distinct().count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ownerConflictsCannotAcknowledgeOrRepublishAnotherDelivery() throws Exception {
        OutboundSpool spool = new OutboundSpool(config);
        spool.enqueue("netmail:first", link, message());
        Path packet = spool.pending(link, true).get(0);
        byte[] original = Files.readAllBytes(packet);
        Files.writeString(packet.resolveSibling(packet.getFileName() + ".owner"),
                          identity("netmail:other"));
        assertThrows(IOException.class, () -> spool.acknowledge(packet));
        assertThrows(IOException.class, () -> spool.enqueue("netmail:first", link, message()));
        assertThrows(IOException.class, () -> spool.pending(link, true));
        assertFalse(spool.isAcknowledged("netmail:first", link));
        assertArrayEquals(original, Files.readAllBytes(packet));
    }
}
