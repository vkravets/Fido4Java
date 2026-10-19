package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.script.FileEchoEvent;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

class FileEchoDeliveryTest {
    @TempDir
    Path root;
    private MailDatabaseFacade base;
    private MailAutomationRuntime scripts;
    private FileEchoProcessor processor;
    private Path inbox;
    private Path ticket;
    private Tosser tosser;
    private JFtnConfig config;
    private String configurationText;

    @BeforeEach
    void open() throws Exception {
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nFileEchoRoot="
                      + root.resolve("areas")
                      + "\nLink=1:500/101,1:500/100,session-password,localhost"
                      + "\nRobotAccessSource=files\nTicPeer=1:500/101|tic-password|lists.*\n";
        configurationText = text;
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:delivery" + System.nanoTime()));
        assertTrue(base.open());
        tosser = new Tosser(config, base);
        scripts = new MailAutomationRuntime(config, base, tosser);
        processor = (FileEchoProcessor) scripts.getFileEcho();
        processor.store().savePolicy("1:500/101", "lists.*", "WRITE");
        inbox = root.resolve("in/peers/1-500-101-0");
        Files.createDirectories(inbox);
        ticket = inbox.resolve("renamed-ticket");
        ReceivedFile.record(ticket, "LIST.TIC");
    }

    private String tic(String password, String checksum) {
        return "Area lists.nodes\r\nFile LIST.001\r\nOrigin 1:500/99\r\nFrom 1:500/101\r\n"
               + "Pw "
               + password
               + "\r\nCrc "
               + checksum
               + "\r\nPath 1:500/101 1700000000\r\nSeenby 1:500/101\r\n";
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

    @Test
    void preservesOriginalNameAndIsolatesCollisionsWithoutPublishingThem() throws Exception {
        Path payload = inbox.resolve("payload");
        ReceivedFile.record(payload, "LIST.001");
        List<FileEchoEvent> events = new ArrayList<>();
        scripts.onFileEcho(events::add);
        AtomicInteger deliveries = new AtomicInteger();
        scripts.onFile((area, path, name, peer, receipt) -> deliveries.incrementAndGet());
        receive(payload, "first");
        Path directory = Path.of(processor.store().area("lists.nodes").directory);
        assertEquals("first", Files.readString(directory.resolve("LIST.001")));
        assertEquals("accepted", events.get(0).status);
        assertEquals(5L, events.get(0).size);
        assertEquals(Instant.ofEpochSecond(1700000000), events.get(0).date);
        receive(payload, "second");
        assertEquals("first", Files.readString(directory.resolve("LIST.001")));
        assertEquals("duplicate", events.get(1).status);
        assertEquals(directory.resolve("dupes").resolve(events.get(1).receipt).resolve("LIST.001"),
                     events.get(1).file);
        assertEquals("second", Files.readString(events.get(1).file));
        assertEquals(1, deliveries.get());
        scripts.beforeExport(tosser);
        assertEquals(2, events.size());
    }

    private void receive(Path payload, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        Files.write(payload, bytes);
        Files.writeString(ticket, tic("tic-password", String.format("%08X", crc.getValue())));
        scripts.beforeExport(tosser);
    }

    @Test
    void replacesOldActivePayloadAndDoesNotRemoveNewFileAfterCallbackFailure() throws Exception {
        Path payload = inbox.resolve("payload");
        ReceivedFile.record(payload, "LIST.001");
        receive(payload, "old contents");
        Path directory = Path.of(processor.store().area("lists.nodes").directory);
        Files.writeString(payload, "new contents");
        Files.writeString(ticket,
                          tic("tic-password", FileChecksum.read(payload).crc)
                          + "Replaces LIST.???\r\n");
        AtomicInteger attempts = new AtomicInteger();
        scripts.onFile((area, path, name, peer, receipt) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("Simulated callback failure after replacement");
            }
        });
        scripts.beforeExport(tosser);
        scripts.beforeExport(tosser);
        assertEquals(2, attempts.get());
        assertEquals("new contents", Files.readString(directory.resolve("LIST.001")));
        try (var archived = Files.walk(directory.resolve("dupes"))) {
            List<Path> copies = archived.filter(path -> path.getFileName()
                                                            .toString()
                                                            .equals("LIST.001"))
                                        .collect(Collectors.toList());
            assertEquals(1, copies.size());
            assertEquals("old contents", Files.readString(copies.get(0)));
        }
    }

    @Test
    void resumesStoredDeliveryAfterCallbackFailureWithoutTurningItIntoADuplicate()
            throws Exception {
        Path payload = inbox.resolve("payload");
        ReceivedFile.record(payload, "LIST.001");
        AtomicInteger attempts = new AtomicInteger();
        scripts.onFile((area, path, name, peer, receipt) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("Retry this callback");
            }
        });
        receive(payload, "stable");
        scripts.beforeExport(tosser);
        assertEquals(2, attempts.get());
        Path directory = Path.of(processor.store().area("lists.nodes").directory);
        assertEquals("stable", Files.readString(directory.resolve("LIST.001")));
        assertTrue(Files.notExists(directory.resolve("dupes")));
    }

    @Test
    void createsOnlyAfterPayloadAndPasswordValidationThenPublishesExactlyOnce() throws Exception {
        byte[] bytes = "directory contents".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        String checksum = String.format("%08X", crc.getValue());
        Files.writeString(ticket, tic("tic-password", checksum));
        scripts.beforeExport(tosser);
        assertNull(processor.store().area("lists.nodes"));
        Path payload = inbox.resolve("renamed-list");
        Files.write(payload, bytes);
        ReceivedFile.record(payload, "LIST.001");
        Files.writeString(ticket, tic("wrong", checksum));
        scripts.beforeExport(tosser);
        assertNull(processor.store().area("lists.nodes"));
        Files.writeString(ticket, tic("tic-password", "00000000"));
        scripts.beforeExport(tosser);
        assertNull(processor.store().area("lists.nodes"));
        AtomicInteger deliveries = new AtomicInteger();
        scripts.onFile((area, path, name, peer, receipt) -> deliveries.incrementAndGet());
        Files.writeString(ticket, tic("tic-password", checksum));
        scripts.beforeExport(tosser);
        scripts.beforeExport(tosser);
        assertEquals(1, deliveries.get());
        assertEquals("lists.nodes", processor.store().area("lists.nodes").name);
        assertTrue(processor.store().canReceive("lists.nodes", new FTNAddr("1:500/101")));
        assertTrue(Files.exists(payload));
        assertTrue(Files.exists(ticket));
    }

    @Test
    void malformedPathCannotCreateAnAreaOrPublishPayloadAndCanBeRetried() throws Exception {
        byte[] bytes = "directory contents".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        String valid = tic("tic-password", String.format("%08X", crc.getValue()));
        Path payload = inbox.resolve("renamed-list");
        Files.write(payload, bytes);
        ReceivedFile.record(payload, "LIST.001");
        AtomicInteger deliveries = new AtomicInteger();
        scripts.onFile((area, path, name, peer, receipt) -> deliveries.incrementAndGet());
        Files.writeString(ticket, valid.replace("Path 1:500/101 1700000000", "Path 1:500/101"));
        scripts.beforeExport(tosser);
        assertNull(processor.store().area("lists.nodes"));
        assertEquals(0, deliveries.get());
        assertTrue(Files.exists(ticket));
        assertTrue(Files.exists(payload));
        Files.writeString(ticket, valid);
        scripts.beforeExport(tosser);
        assertEquals(1, deliveries.get());
    }

    @Test
    void acceptsLegalDosPunctuationInPayloadNames() throws Exception {
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        Path payload = inbox.resolve("payload");
        Files.write(payload, bytes);
        ReceivedFile.record(payload, "TOOLS$.ZIP");
        Files.writeString(ticket,
                          tic("tic-password",
                              String.format("%08X", crc.getValue())).replace("File LIST.001",
                                                                             "File TOOLS$.ZIP"));
        AtomicInteger deliveries = new AtomicInteger();
        scripts.onFile((area, path, name, peer, receipt) -> {
            assertEquals("TOOLS$.ZIP", name);
            deliveries.incrementAndGet();
        });
        scripts.beforeExport(tosser);
        assertEquals(1, deliveries.get());
    }

    @Test
    void extensionValidatesFieldsAndNeverReturnsStoredPasswords() throws Exception {
        scripts.close();
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(configurationText.replace("RobotAccessSource=files",
                                                                       "RobotAccessSource=database")
                                                              .getBytes(StandardCharsets.UTF_8)));
        scripts = new MailAutomationRuntime(config, base, tosser);
        SettingsExtension extension = scripts.getSettingsExtensions().get(0);
        extension.apply("robots",
                        Map.of("service",
                               "Tic",
                               "peer",
                               "1:500/101",
                               "masks",
                               "lists.*",
                               "enabled",
                               "true",
                               "password",
                               "private-tic-password"));
        SettingsExtension.Section robots = extension.describe().sections.stream()
                                                                        .filter(section -> section.id.equals("robots"))
                                                                        .findFirst()
                                                                        .orElseThrow();
        assertEquals("", robots.rows.get(0).get("password"));
        extension.apply("robots",
                        Map.of("service",
                               "Tic",
                               "peer",
                               "1:500/101",
                               "masks",
                               "lists.*,new.*",
                               "enabled",
                               "true",
                               "password",
                               ""));
        assertTrue(scripts.getRobotAccess()
                          .find("Tic", new FTNAddr("1:500/101"))
                          .verifyPassword("private-tic-password"));
        assertThrows(IllegalArgumentException.class,
                     () -> extension.apply("areas", Map.of("name", "missing-fields")));
        assertThrows(IllegalArgumentException.class, () -> extension.apply("unknown", Map.of()));
    }

    @Test
    void relaysHtickMetadataWithNewPeerCredentialsAndPreservedPayload() throws Exception {
        scripts.close();
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream((configurationText
                + "Link=1:500/103,1:500/100,other-session,localhost\n"
                + "TicPeer=1:500/103|outgoing-tic|lists.*\n").getBytes(StandardCharsets.UTF_8)));
        scripts = new MailAutomationRuntime(config, base, tosser);
        processor = (FileEchoProcessor) scripts.getFileEcho();
        processor.store()
                 .saveArea("lists.nodes", root.resolve("areas/nodes").toString(), "Lists", true);
        processor.store().saveAccess("lists.nodes", "1:500/101", "WRITE");
        processor.store().subscribe(new FTNAddr("1:500/103"), "lists.nodes", true);
        Path payload = inbox.resolve("payload");
        ReceivedFile.record(payload, "LIST.001");
        Files.writeString(payload, "payload");
        String input = tic("TIC-PASSWORD", FileChecksum.read(payload).crc)
                       + "Date 757382400\r\nSize 000007\r\nTo Test Sysop, 1:500/100\r\n"
                       + "Desc Original description\r\nX-Extra preserved\r\nSeenby 1:500/99\r\n";
        Files.writeString(ticket, input);
        List<FileEchoEvent> events = new ArrayList<>();
        scripts.onFileEcho(events::add);
        scripts.beforeExport(tosser);
        scripts.beforeExport(tosser);
        assertEquals(1, events.size());
        assertEquals(Instant.parse("1994-01-01T00:00:00Z"), events.get(0).date);
        List<Path> queued = scripts.getFileQueue()
                                   .pending(config.getLink(new FTNAddr("1:500/103")));
        assertEquals(2, queued.size());
        assertEquals("LIST.001", queued.get(0).getFileName().toString());
        assertEquals("payload", Files.readString(queued.get(0)));
        String outgoing = Files.readString(queued.get(1));
        assertTrue(outgoing.contains("Origin 1:500/99\r\n"));
        assertTrue(outgoing.contains("Date 757382400\r\n"));
        assertTrue(outgoing.contains("From 1:500/100\r\nTo 1:500/103\r\nPw outgoing-tic\r\n"));
        assertTrue(outgoing.contains("X-Extra preserved\r\n"));
        assertTrue(outgoing.contains("Path 1:500/101 1700000000\r\nPath 1:500/100 "));
        assertTrue(outgoing.contains("Seenby 1:500/99\r\nSeenby 1:500/100\r\n"
                                     + "Seenby 1:500/101\r\nSeenby 1:500/103\r\n"));
        scripts.getFileQueue().acknowledge(queued.get(0));
        assertEquals(List.of(queued.get(1)),
                     scripts.getFileQueue().pending(config.getLink(new FTNAddr("1:500/103"))));
        scripts.getFileQueue().acknowledge(queued.get(1));
        scripts.beforeExport(tosser);
        assertEquals(List.of(),
                     scripts.getFileQueue().pending(config.getLink(new FTNAddr("1:500/103"))));
        assertEquals(input, Files.readString(ticket));
    }
}
