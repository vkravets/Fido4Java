package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.zip.CRC32;

/** Executes the installed artifacts with no test/application classes added to their classpath. */
class ProfileDistributionTest {
    @TempDir
    Path temporary;

    @Test
    void startedHeadlessWithSqlAndWithoutBrowserClasses() throws Exception {
        Path home = unpack("headless");
        installConfiguration(home, "headless");
        Files.writeString(home.resolve("jftn.conf"),
                          Files.readString(home.resolve("jftn.conf"))
                               .replace("FileEchoSource=files", "FileEchoSource=database"));
        try (var plugins = Files.list(home.resolve("plugins"))) {
            assertTrue(plugins.anyMatch(path -> path.getFileName()
                                                    .toString()
                                                    .startsWith("fileecho-storage-h2-")));
        }
        try (var paths = Files.walk(home)) {
            assertFalse(paths.anyMatch(path -> path.getFileName()
                                                   .toString()
                                                   .matches("(rpc-|web-ui-).*\\.jar")));
        }
        verifyServer(home);
        assertTrue(Files.exists(home.resolve("database/jftn10.mv.db")));
    }

    @ParameterizedTest
    @CsvSource({"legacy,jam", "legacy,msg", "transit,jam"})
    void processedPacketsAndTicsWithoutSqlOrUiAndRecoveredOnRestart(String profile, String format)
            throws Exception {
        Path home = unpack(profile);
        try (var paths = Files.walk(home)) {
            assertFalse(paths.anyMatch(path -> path.getFileName()
                                                   .toString()
                                                   .matches("(fido-database|h2-|ormlite-|rpc-|web-ui-|storage-h2-|directory-storage-h2-|account-storage-h2-|fileecho-storage-h2-).*\\.jar")));
        }
        installConfiguration(home, profile);
        Files.writeString(home.resolve("jftn.conf"),
                          Files.readString(home.resolve("jftn.conf"))
                          + "LegacyNetmailFormat="
                          + format
                          + "\n");
        Path inbox = home.resolve("mail/inbound/peers/1-500-101-0");
        Files.createDirectories(inbox);
        Files.write(inbox.resolve("00000001.pkt"),
                    PacketWriter.write(new FTNAddr("1:500/101"),
                                       new FTNAddr("1:500/100"),
                                       "",
                                       List.of(message("test", "1:500/100", "11111111"),
                                               message(null, "1:500/999", "22222222"),
                                               message(null, "1:500/100", "33333333"))));
        receiveTic(inbox);
        process(home);
        assertTrue(Files.exists(home.resolve("fileecho/lists.test/TEST.TXT")));
        assertFalse(Files.exists(inbox.resolve("00000001.pkt")));
        assertFalse(Files.exists(home.resolve("database")));
        long packets = packets(home);
        assertEquals(2, packets, Files.readString(home.resolve("installed-node.log")));
        process(home);
        assertEquals(packets, packets(home));
        verifyServer(home);
        assertFalse(Files.exists(home.resolve("database")));
        if (profile.equals("legacy")) {
            assertTrue(Files.exists(home.resolve(format.equals("msg")
                    ? "mail/echo/netmail-msg/1.msg"
                    : "mail/echo/netmail.jhr")));
            assertTrue(Files.exists(home.resolve("mail/echo/echo-test.jhr")));
            assertTrue(Files.readString(home.resolve("editor/areas.lst")).contains("AREADEF test"));
        } else {
            try (var paths = Files.walk(home)) {
                assertFalse(paths.anyMatch(path -> path.toString().endsWith(".jhr")));
            }
        }
    }

    private long packets(Path home) throws Exception {
        try (var paths = Files.walk(home.resolve("mail/outbound"))) {
            return paths.filter(path -> path.toString().endsWith(".pkt")).count();
        }
    }

    private Message message(String area, String destination, String id) {
        return new Message("Remote",
                           "Sysop",
                           new FTNAddr("1:500/101"),
                           new FTNAddr(destination),
                           "Test",
                           (area == null ? "" : "AREA:" + area + "\r")
                           + "\1MSGID: 1:500/101 "
                           + id
                           + "\r\1CHRS: UTF-8 4\rMessage С— С”\r",
                           new Date(1700000000000L));
    }

    private void installConfiguration(Path home, String profile) throws Exception {
        InstalledNodeFixture.configure(home, profile);
    }

    private void receiveTic(Path inbox) throws Exception {
        byte[] content = "Received test file".getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(content);
        Path payload = inbox.resolve("payload.dat");
        ReceivedFile.record(payload, "TEST.TXT");
        Files.write(payload, content);
        Path ticket = inbox.resolve("delivery.TIC");
        ReceivedFile.record(ticket, "00000001.TIC");
        Files.writeString(ticket,
                          "Area lists.test\r\nOrigin 1:500/101\r\nFrom 1:500/101\r\nFile TEST.TXT\r\nSize "
                                  + content.length + "\r\nCrc " + Long.toHexString(crc.getValue())
                                  + "\r\nDesc Received test file\r\nPw tic-password\r\n");
    }

    private void process(Path home) throws Exception {
        try (InstalledNodeFixture.NodeProcess process = InstalledNodeFixture.start(home,
                                                                                   "--process-mail")) {
            assertEquals(0, process.awaitExit(), process.output());
        }
    }

    private void verifyServer(Path home) throws Exception {
        try (InstalledNodeFixture.NodeProcess server = InstalledNodeFixture.start(home)) {
            try (Socket socket = new Socket("127.0.0.1", server.port(InstalledNodeFixture.BINKP))) {
                socket.setSoTimeout(5000);
                assertTrue(socket.getInputStream().read() >= 0, "Missing binkp greeting");
            }
        }
    }

    private Path unpack(String profile) throws Exception {
        return InstalledNodeFixture.unpack(temporary, profile);
    }
}
