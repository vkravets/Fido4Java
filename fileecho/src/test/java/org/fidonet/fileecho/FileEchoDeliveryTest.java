package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.script.MailScripts;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

class FileEchoDeliveryTest {
    @TempDir
    Path root;
    private DatabaseManager base;
    private MailScripts scripts;
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
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:delivery" + System.nanoTime()));
        assertTrue(base.open());
        tosser = new Tosser(config, base);
        scripts = new MailScripts(config, base, tosser);
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
    void extensionValidatesFieldsAndNeverReturnsStoredPasswords() throws Exception {
        scripts.close();
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(configurationText.replace("RobotAccessSource=files",
                                                                       "RobotAccessSource=database")
                                                              .getBytes(StandardCharsets.UTF_8)));
        scripts = new MailScripts(config, base, tosser);
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
}
