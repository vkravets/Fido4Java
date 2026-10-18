package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.plugins.OfflineCommand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.zip.CRC32;

class FileEchoIndexCommandTest {
    @TempDir
    Path root;

    private String portable(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    @Test
    void rejectsUnavailableCommandsInsteadOfStartingTheStation() {
        assertThrows(IllegalArgumentException.class,
                     () -> JFtnRunner.main(new String[]{"--unavailable-offline-command"}));
    }

    @Test
    void rebuildsThroughOptionalCommandAndExternalScriptWithoutDatabaseOrTossing()
            throws Exception {
        Path area = Files.createDirectory(root.resolve("area"));
        Path archive = Files.createDirectory(root.resolve("received"));
        Path payload = Files.writeString(area.resolve("LIST.001"), "payload");
        Files.writeString(area.resolve("OTHER.ZIP"), "other");
        Path index = Files.writeString(area.resolve("file.bbs"), "stale entry\r\n");
        CRC32 crc = new CRC32();
        crc.update(Files.readAllBytes(payload));
        Files.writeString(archive.resolve("test.TIC"),
                          "Area lists\r\nFile LIST.001\r\n"
                                  + "Origin 1:500/101\r\nFrom 1:500/101\r\nPath 1:500/101 1\r\nSeenby 1:500/100\r\n"
                                  + "Crc " + String.format("%08X", crc.getValue())
                                  + "\r\nDesc Український опис ${name}\r\nLDesc second line\r\n",
                          StandardCharsets.UTF_8);
        Path config = root.resolve("jftn.conf");
        Files.writeString(config,
                          "SysOp=Test\nLink=1:500/101,1:500/100,secret,localhost\n" + "Inbound="
                                  + portable(root.resolve("in")) + "\nOutbound="
                                  + portable(root.resolve("out")) + "\nEchoPath="
                                  + portable(root.resolve("echo")) + "\nTmp="
                                  + portable(root.resolve("tmp")) + "\nReceivedArchivePath="
                                  + portable(archive)
                                  + "\nTicCharset=UTF-8\nFileEchoIndexName=file.bbs\nMailScript="
                                  + portable(Path.of("src/main/resources/mail-scripts/fileecho-tools.groovy"))
                                  + "\n");
        OfflineCommand command = ServiceLoader.load(OfflineCommand.class).iterator().next();
        String[] arguments = {"--rebuild-fileecho-index", "lists", area.toString(),
                config.toString()};
        assertTrue(command.handle(arguments));
        String expected = "LIST.001 Український опис ${name} | second line\r\nOTHER.ZIP\r\n";
        assertEquals(expected, Files.readString(index));
        try (var files = Files.list(area)) {
            Path backup = files.filter(file -> file.getFileName()
                                                   .toString()
                                                   .startsWith(".file.bbs-backup-"))
                               .findFirst()
                               .orElseThrow();
            assertEquals("stale entry\r\n", Files.readString(backup));
        }
        assertTrue(command.handle(arguments));
        assertEquals(expected, Files.readString(index));
        assertEquals("payload", Files.readString(payload));
        assertTrue(Files.notExists(root.resolve("database")));
    }
}
