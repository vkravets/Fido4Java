package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.PartialFileOutputStream;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DirectoryInboundScriptTest {
    @TempDir
    Path root;

    private JFtnConfig config(String extra) throws Exception {
        return config(extra, true);
    }

    private JFtnConfig config(String extra, boolean checksum) throws Exception {
        String prefix = root.toString().replace('\\', '/');
        String text = "SysOp=Test\nInbound="
                      + prefix
                      + "/in\nOutbound="
                      + prefix
                      + "/out\nTmp="
                      + prefix
                      + "/state\nEchoPath="
                      + prefix
                      + "/echo\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\n"
                      + "Link=1:500/102,1:500/100,secret,localhost\n"
                      + "JamBridgeEnabled=false\nNodeDirectoryEnabled=false\n"
                      + "MailScript=classpath:mail-scripts/directory-import.groovy\n"
                      + "NodeDirectoryPath="
                      + prefix
                      + "/catalog\n"
                      + "DirectoryInboundFeed=nodes|NODELIST|NODELIST.*|NODEDIFF.*|"
                      + prefix
                      + "/base/nodes.lst|1:500/101|1|500|US-ASCII|"
                      + checksum
                      + "\n"
                      + "DirectoryInboundFeed=points|BOSS|PNT46REG.*||"
                      + prefix
                      + "/base/points.lst|1:500/101\n"
                      + "DirectoryInboundFeed=region|NODELIST|REGION46.*||"
                      + prefix
                      + "/base/region.lst|1:500/101|1|500\n"
                      + extra;
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private DatabaseManager database() {
        DatabaseManager base = new DatabaseManager(new OrmManager("jdbc:h2:mem:inboundDirectory"
                                                                  + System.nanoTime()));
        assertTrue(base.open());
        return base;
    }

    private String checked(String body, LocalDate date) {
        int crc = 0;
        for (byte octet : body.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (octet & 255) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc & 0x8000) == 0 ? crc << 1 : (crc << 1) ^ 0x1021) & 65535;
            }
        }
        String when = date.format(DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH));
        return ";A FidoNet Nodelist for Friday, "
               + when
               + " -- Day number "
               + date.getDayOfYear()
               + " : "
               + String.format("%05d", crc)
               + "\r\n"
               + body;
    }

    private String nodes(String system, int year, int month, int day) {
        return checked(",101," + system + ",City,Sysop,-Unpublished-,300\r\n",
                       LocalDate.of(year, month, day));
    }

    private byte[] zip(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("list.txt"));
            zip.write(text.getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private Path receive(String name, byte[] bytes, String peer) throws Exception {
        Path directory = root.resolve("in/peers/" + ReceivedFile.directoryName(new FTNAddr(peer)));
        Files.createDirectories(directory);
        Path path = Files.createTempFile(directory, "payload-", ".file");
        Files.write(path, bytes);
        ReceivedFile.record(path, name);
        return path;
    }

    private Path receive(String name, String text) throws Exception {
        return receive(name, text.getBytes(StandardCharsets.US_ASCII), "1:500/101");
    }

    private String system(String source, String address) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:"
                                                                 + root.resolve("catalog"));
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT system_name FROM directory_entries WHERE source_id='"
                        + source + "' AND address='" + address + "'")) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private void clearInbox(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.delete(file);
            }
        }
    }

    @Test
    void importedNewestBacklogNodeRegionAndPointListsAndDidNotDowngradeAfterRestart()
            throws Exception {
        JFtnConfig config = config("DirectoryInboundEnabled=true\n");
        Path old = receive("NODELIST.Z89", zip(nodes("Old", 2025, 10, 16)), "1:500/101");
        receive("NODELIST.Z02", zip(nodes("New", 2026, 1, 2)), "1:500/101");
        receive("PNT46REG.002",
                checked("Boss,1:500/101\r\n,1,Point,City,Sysop,-Unpublished-,300\r\n",
                        LocalDate.of(2026, 1, 2)));
        receive("REGION46.Z02", zip(nodes("Region", 2026, 1, 2)), "1:500/101");
        DatabaseManager base = database();
        try {
            try (MailService service = new MailService(config, base)) {
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
                assertEquals("Region", system("region", "1:500/101.0"));
                assertEquals("Point", system("points", "1:500/101.1"));
                assertTrue(Files.exists(old));
                receive("NODELIST.001", nodes("Stale", 2025, 12, 31));
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
            }
            Path retained = root.resolve("base/nodes.lst");
            var modified = Files.getLastModifiedTime(retained);
            try (MailService service = new MailService(config, base)) {
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
                assertEquals(modified, Files.getLastModifiedTime(retained));
            }
        } finally {
            base.close();
        }
    }

    @Test
    void appliedDiffOnceAndRetainedTheGoodEditionAfterInvalidNewInput() throws Exception {
        JFtnConfig config = config("DirectoryInboundEnabled=true\n");
        String old = nodes("Old", 2026, 10, 9);
        String next = nodes("New", 2026, 10, 16);
        DatabaseManager base = database();
        try {
            try (MailService service = new MailService(config, base)) {
                receive("NODELIST.282", old);
                service.processPending();
                receive("NODEDIFF.289",
                        old.substring(0, old.indexOf('\r')) + "\r\nD2\r\nA2\r\n" + next);
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
                receive("NODELIST.296", nodes("Good", 2026, 10, 23).replace("Good", "Evil"));
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
            }
            try (MailService service = new MailService(config, base)) {
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
            }
        } finally {
            base.close();
        }
    }

    @Test
    void remainedDisabledByDefaultAndIgnoredUnauthorizedOrUnrecordedFiles() throws Exception {
        receive("NODELIST.289", nodes("Disabled", 2026, 10, 16));
        DatabaseManager base = database();
        try {
            try (MailService service = new MailService(config(""), base)) {
                service.processPending();
                assertFalse(Files.exists(root.resolve("catalog.mv.db")));
            }
            Path authorized = root.resolve("in/peers/1-500-101-0");
            clearInbox(authorized);
            try (MailService service = new MailService(config("DirectoryInboundEnabled=true\n"),
                                                       base)) {
                receive("NODELIST.289",
                        nodes("WrongPeer", 2026, 10, 16).getBytes(StandardCharsets.US_ASCII),
                        "1:500/102");
                Path unrecorded = Files.writeString(authorized.resolve("NODELIST.289"),
                                                    nodes("NoMetadata", 2026, 10, 16));
                receive("UNRELATED.289", nodes("WrongName", 2026, 10, 16));
                service.processPending();
                assertNull(system("nodes", "1:500/101.0"));
                assertTrue(Files.exists(unrecorded));
            }
        } finally {
            base.close();
        }
    }

    @Test
    void importedAutomaticallyWhenTheTransportCommittedAFileWithoutATic() throws Exception {
        JFtnConfig config = config("DirectoryInboundEnabled=true\n");
        DatabaseManager base = database();
        try (MailService service = new MailService(config, base)) {
            SessionContext context = JFtnRunner.createSessionContext(config);
            context.getLinksInfo().setCurLink(new FTNAddr("1:500/101"));
            service.attach(context);
            byte[] bytes = nodes("Automatic", 2026, 10, 16).getBytes(StandardCharsets.US_ASCII);
            PartialFileOutputStream partial = new PartialFileOutputStream(root.resolve("state/arrival.part"),
                                                                          bytes.length);
            partial.write(bytes);
            partial.close();
            context.commitReceivedFile(new FileData<>(new FileInfo("NODELIST.289", bytes.length, 1),
                                                      partial));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!"Automatic".equals(system("nodes", "1:500/101.0"))
                    && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertEquals("Automatic", system("nodes", "1:500/101.0"));
        } finally {
            base.close();
        }
    }

    @Test
    void retriedAnEarlyDiffAfterTheMissingBaseArrivedAndTheServiceRestarted() throws Exception {
        JFtnConfig config = config("DirectoryInboundEnabled=true\n");
        String old = nodes("Old", 2026, 10, 9);
        String next = nodes("New", 2026, 10, 16);
        DatabaseManager base = database();
        try {
            try (MailService service = new MailService(config, base)) {
                receive("NODEDIFF.289",
                        old.substring(0, old.indexOf('\r')) + "\r\nD2\r\nA2\r\n" + next);
                service.processPending();
                assertNull(system("nodes", "1:500/101.0"));
                receive("NODELIST.282", old);
                service.processPending();
                assertEquals("Old", system("nodes", "1:500/101.0"));
            }
            try (MailService service = new MailService(config, base)) {
                service.processPending();
                assertEquals("New", system("nodes", "1:500/101.0"));
            }
        } finally {
            base.close();
        }
    }

    @Test
    void requiredExplicitOptOutToImportAnUndatedFragment() throws Exception {
        receive("NODELIST.289", ",101,Fragment,City,Sysop,-Unpublished-,300\r\n");
        DatabaseManager base = database();
        try {
            try (MailService service = new MailService(config("DirectoryInboundEnabled=true\n",
                                                              false),
                                                       base)) {
                service.processPending();
                assertNull(system("nodes", "1:500/101.0"));
            }
            JFtnConfig allowed = config("DirectoryInboundEnabled=true\nDirectoryInboundRequireDate=false\n",
                                        false);
            try (MailService service = new MailService(allowed, base)) {
                service.processPending();
                assertEquals("Fragment", system("nodes", "1:500/101.0"));
            }
        } finally {
            base.close();
        }
    }

    @Test
    void refusedUntrustedPeersAndInvalidOptionsBeforeStartingImport() throws Exception {
        DatabaseManager base = database();
        try {
            assertThrows(IOException.class,
                         () -> new MailService(config("DirectoryInboundEnabled=maybe\n"), base));
            assertThrows(IOException.class,
                         () -> new MailService(config("DirectoryInboundEnabled=true\nDirectoryInboundRetrySeconds=0\n"),
                                               base));
            assertThrows(IOException.class,
                         () -> new MailService(config("DirectoryInboundEnabled=true\n"
                                 + "DirectoryInboundFeed=other|NODELIST|OTHER.*||"
                                 + root.resolve("base/other.lst") + "|1:500/999\n"), base));
        } finally {
            base.close();
        }
    }
}
