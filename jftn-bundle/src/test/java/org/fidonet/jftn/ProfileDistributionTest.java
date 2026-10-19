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

import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Executes the installed artifacts with no test/application classes added to their classpath. */
class ProfileDistributionTest {
    @TempDir
    Path temporary;

    @Test
    void startedHeadlessWithSqlAndWithoutBrowserClasses() throws Exception {
        Path home = unpack("headless");
        installConfiguration(home, "headless");
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
                                                   .matches("(fido-database|h2-|ormlite-|rpc-|web-ui-).*\\.jar")));
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
        assertEquals(2, packets, Files.readString(home.resolve("profile-smoke.log")));
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
                           + "\r\1CHRS: UTF-8 4\rMessage ї є\r",
                           new Date(1700000000000L));
    }

    private void installConfiguration(Path home, String profile) throws Exception {
        try (var fragments = Files.list(home.resolve("conf.d"))) {
            for (Path path : fragments.filter(path -> path.toString().endsWith(".conf.example"))
                                      .collect(Collectors.toList())) {
                Files.copy(path,
                           path.resolveSibling(path.getFileName()
                                                   .toString()
                                                   .replace(".example", "")));
            }
        }
        Files.writeString(home.resolve("jftn.conf"),
                          "SysOp=Test\nRuntimeProfile=" + profile
                                  + "\nLink=1:500/101,1:500/100,-\nLink=1:500/102,1:500/100,-\n"
                                  + "Inbound=./mail/inbound\nOutbound=./mail/outbound\nTmp=./mail/tmp\nEchoPath=./mail/echo\n"
                                  + "RpcEnabled=false\nRobotAccessSource=files\nFileEchoSource=files\n"
                                  + "NodeDirectoryEnabled=false\nJamBridgeEnabled=false\n"
                                  + "EchoArea=test,1:500/101=WRITE,1:500/102=READ\n"
                                  + "DefaultRoute=1:500/102\nAllowTransit=true\nTransitFrom=1:500/101\nTransitTo=*\nBinkpPort=0\n"
                                  + "FileArea=lists.test|./fileecho/lists.test|Test files\nTicPeer=1:500/101|tic-password|lists.*\n");
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
        Path output = home.resolve("profile-smoke.log");
        String classpath = String.join(File.pathSeparator,
                                       "scripts",
                                       "lib/*",
                                       "lib/groovy/*",
                                       "plugins/*");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                                                 .toString(),
                                             "-cp",
                                             classpath,
                                             JFtnRunner.class.getName(),
                                             "--process-mail").directory(home.toFile())
                                                              .redirectErrorStream(true)
                                                              .redirectOutput(output.toFile())
                                                              .start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Profile process timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            process.destroyForcibly();
        }
    }

    private void verifyServer(Path home) throws Exception {
        Files.writeString(home.resolve("conf.d/26-bso.conf"), "BsoEnabled=true\n");
        Path output = home.resolve("server-smoke.log");
        Process server = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                                                .toString(),
                                            "-cp",
                                            String.join(File.pathSeparator,
                                                        "scripts",
                                                        "lib/*",
                                                        "lib/groovy/*",
                                                        "plugins/*"),
                                            JFtnRunner.class.getName()).directory(home.toFile())
                                                                       .redirectErrorStream(true)
                                                                       .redirectOutput(output.toFile())
                                                                       .start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            Matcher listening = Pattern.compile("binkp server listening: port=([0-9]+)")
                                       .matcher("");
            while (System.nanoTime() < deadline) {
                listening = Pattern.compile("binkp server listening: port=([0-9]+)")
                                   .matcher(Files.readString(output));
                if (listening.find()) {
                    try (Socket socket = new Socket("127.0.0.1",
                                                    Integer.parseInt(listening.group(1)))) {
                        socket.setSoTimeout(5000);
                        assertTrue(socket.getInputStream().read() >= 0,
                                   "Server did not send a binkp greeting");
                    }
                    return;
                }
                assertTrue(server.isAlive(), Files.readString(output));
                Thread.sleep(100);
            }
            throw new AssertionError("Profile server did not start: " + Files.readString(output));
        } finally {
            server.destroy();
            if (!server.waitFor(10, TimeUnit.SECONDS)) {
                server.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private Path unpack(String profile) throws Exception {
        Path destination = temporary.resolve(profile);
        Files.createDirectories(destination);
        Path archive = Path.of(System.getProperty("profile.distribution." + profile));
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                assertTrue(target.startsWith(destination));
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        try (var paths = Files.list(destination)) {
            return paths.filter(Files::isDirectory).findFirst().orElseThrow();
        }
    }
}
