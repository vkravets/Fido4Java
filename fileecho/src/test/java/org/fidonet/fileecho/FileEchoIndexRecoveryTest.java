package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class FileEchoIndexRecoveryTest {
    @TempDir
    Path root;

    private Path area() throws IOException {
        return Files.createDirectories(root.resolve("area"));
    }

    private Path archive() throws IOException {
        return Files.createDirectories(root.resolve("archive"));
    }

    private void tic(String id, String area, Path payload, String crc, String description)
            throws IOException {
        Path directory = Files.createDirectories(archive().resolve(id));
        String text = "Area "
                      + area
                      + "\r\nFile "
                      + payload.getFileName()
                      + "\r\nOrigin 1:500/101\r\nFrom 1:500/101\r\nCrc "
                      + crc
                      + "\r\nSize "
                      + Files.size(payload)
                      + "\r\nPath 1:500/101 1\r\nSeenby 1:500/100\r\nDesc "
                      + description
                      + "\r\n";
        Files.writeString(directory.resolve("delivery.TIC"), text, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> recover() throws IOException {
        return FileEchoIndexRecovery.recover("lists", area(), archive(), StandardCharsets.UTF_8);
    }

    @Test
    void recoversDescriptionsOnlyForMatchingAreaContentAndSize() throws Exception {
        Path payload = Files.writeString(area().resolve("LIST.001"), "payload");
        Files.writeString(area().resolve("OTHER.ZIP"), "other");
        Files.writeString(area().resolve("files.bbs"), "stale");
        Files.writeString(area().resolve(".files.bbs-backup-test.bak"), "backup");
        Files.createDirectories(area().resolve("dupes"));
        tic("correct",
            "lists",
            payload,
            FileChecksum.read(payload).crc,
            "Український опис ${name}");
        tic("wrong-area", "elsewhere", payload, FileChecksum.read(payload).crc, "wrong");
        tic("wrong-crc", "lists", payload, "00000000", "wrong");
        Path malformed = archive().resolve("invalid.TIC");
        Files.writeString(malformed, "Pw never-log-this\n");
        List<Map<String, Object>> rows = recover();
        assertEquals(2, rows.size());
        assertEquals(7L, rows.get(0).get("size"));
        assertEquals(Instant.ofEpochSecond(1), rows.get(0).get("date"));
        assertEquals(5L, rows.get(1).get("size"));
        assertEquals(null, rows.get(1).get("date"));
        assertEquals(List.of("Український опис ${name}"), rows.get(0).get("descriptions"));
        assertEquals(List.of(), rows.get(1).get("descriptions"));
        assertEquals("stale", Files.readString(area().resolve("files.bbs")));
        assertEquals("payload", Files.readString(payload));
    }

    @Test
    void refusesConflictingDescriptionsWithoutChangingExistingIndex() throws Exception {
        Path payload = Files.writeString(area().resolve("LIST.001"), "payload");
        tic("one", "lists", payload, FileChecksum.read(payload).crc, "one");
        tic("two", "lists", payload, FileChecksum.read(payload).crc, "two");
        Path index = Files.writeString(area().resolve("files.bbs"), "previous");
        assertThrows(IOException.class, this::recover);
        assertEquals("previous", Files.readString(index));
    }

    @Test
    void ignoresWrongSizeAndRejectsMissingArchive() throws Exception {
        Path payload = Files.writeString(area().resolve("LIST.001"), "payload");
        tic("wrong-size", "lists", payload, FileChecksum.read(payload).crc, "wrong");
        Path ticket = archive().resolve("wrong-size/delivery.TIC");
        Files.writeString(ticket, Files.readString(ticket).replace("Size 7", "Size 999"));
        assertEquals(List.of(), recover().get(0).get("descriptions"));
        assertThrows(IOException.class,
                     () -> FileEchoIndexRecovery.recover("lists",
                                                         area(),
                                                         root.resolve("missing"),
                                                         StandardCharsets.UTF_8));
    }

    @Test
    void usesEarliestFirstHopForIdenticalArchivedCopies() throws Exception {
        Path payload = Files.writeString(area().resolve("LIST.001"), "payload");
        String crc = FileChecksum.read(payload).crc;
        tic("first-sorted", "lists", payload, crc, "same");
        Path later = archive().resolve("first-sorted/delivery.TIC");
        Files.writeString(later,
                          Files.readString(later)
                               .replace("Path 1:500/101 1\r", "Path 1:500/101 999\r"));
        tic("last-sorted", "lists", payload, crc, "same");
        assertEquals(Instant.ofEpochSecond(1), recover().get(0).get("date"));
    }

    @Test
    void refusesOfflineCommandWhileMailRuntimeLockIsHeld() throws Exception {
        Path temporary = Files.createDirectories(root.resolve("tmp"));
        Path config = root.resolve("jftn.conf");
        Files.writeString(config,
                          "SysOp=Test\nLink=1:500/101,1:500/100,secret,localhost\n"
                          + "Inbound="
                          + root.resolve("in")
                          + "\nOutbound="
                          + root.resolve("out")
                          + "\nEchoPath="
                          + root.resolve("echo")
                          + "\nTmp="
                          + temporary
                          + "\n");
        try (FileChannel channel = FileChannel.open(temporary.resolve("mail-service.lock"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            IOException failure = assertThrows(IOException.class,
                                               () -> new FileEchoIndexCommand().handle(new String[]{
                                                       "--rebuild-fileecho-index", "lists",
                                                       area().toString(), config.toString()}));
            assertTrue(failure.getMessage().contains("Stop the station"));
        }
    }
}
