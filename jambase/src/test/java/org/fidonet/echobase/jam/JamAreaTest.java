package org.fidonet.echobase.jam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class JamAreaTest {
    @Test
    public void versionChangesForExternalTextEditsAndHeaderUpdates() throws Exception {
        JamArea area = area();
        area.append(message());
        String initial = area.locked(JamArea.Access::version);
        Path text = Path.of(area.getPath() + ".jdt");
        Files.setLastModifiedTime(text,
                                  FileTime.fromMillis(Files.getLastModifiedTime(text).toMillis()
                                                      + 1000));
        String edited = area.locked(JamArea.Access::version);
        assertFalse(initial.equals(edited));
        area.locked(access -> {
            access.attributes(1, JamArea.SENT, 0);
            assertFalse(edited.equals(access.version()));
            return null;
        });
    }

    @Test
    public void visitsFilteredHeadersAndReadsAssignedIdsByNumber() throws Exception {
        JamArea area = area();
        area.append(message(), JamArea.ECHO);
        area.append(message(), JamArea.ECHO | JamArea.LOCAL);
        area.locked(access -> {
            int[] selected = {0};
            access.forEach(attributes -> (attributes & JamArea.LOCAL) != 0, entry -> {
                selected[0]++;
                assertEquals(2, entry.getNumber());
            });
            assertEquals(1, selected[0]);
            assertEquals(2, access.get(2).getNumber());
            assertEquals(message().getMsgId(), access.get(2).getMessage().getMsgId());
            access.attributes(1, JamArea.DELETED, 0);
            assertDeletedEntryRejected(access);
            return null;
        });
    }

    private void assertDeletedEntryRejected(JamArea.Access access) throws IOException {
        try {
            access.get(1);
            fail("Deleted message must not be returned");
        } catch (IOException expected) {
            assertEquals("JAM message was deleted", expected.getMessage());
        }
    }

    @TempDir
    public File temporary;

    private JamArea area() throws Exception {
        JamArea area = new JamArea(temporary.toPath().resolve("echo"),
                                   "TEST",
                                   new FTNAddr("2:500/1"));
        area.create();
        return area;
    }

    private Message message() {
        Message message = new Message("Author",
                                      "All",
                                      new FTNAddr("2:500/1"),
                                      new FTNAddr("2:500/2"),
                                      "Subject",
                                      "AREA:TEST\r\1MSGID: 2:500/1 ABCD\r\1REPLY: 2:500/2 ABCD\r"
                                              + "\1CHRS: UTF-8 4\r\1FLAGS DIR\r\1PID: Test\rHello\r"
                                              + " * Origin: Test (2:500/1)\rSEEN-BY: 500/1 2\r\1PATH: 500/1\r",
                                      new Date(1700000000000L));
        message.setAttributes(256 | 512 | 1);
        return message;
    }

    @Test
    public void roundTripsTextControlsAndAttributesWithoutDuplicates() throws Exception {
        JamArea area = area();
        area.append(message());
        List<JamArea.Entry> entries = area.read();
        Message stored = entries.get(0).getMessage();
        assertEquals(message().getAttributes(), stored.getAttributes());
        assertEquals("2:500/1 ABCD", stored.getMsgId());
        assertEquals(message().getMsgDate(), stored.getMsgDate());
        assertEquals(1,
                     Arrays.stream(stored.getBody().toString().split("\r"))
                           .filter(line -> line.startsWith("\1MSGID:"))
                           .count());
        String text = Files.readString(Path.of(area.getPath() + ".jdt"));
        assertFalse(text.contains("MSGID"));
        assertFalse(text.contains("SEEN-BY"));
        assertTrue(text.contains("Hello\r * Origin:"));
        assertEquals(0x340bc6d9, JamCodec.crc("123456789".getBytes()));
        assertEquals(JamCodec.crc("ABCD".getBytes()), JamCodec.crc("abcd".getBytes()));
    }

    @Test
    public void refusesRecreationWithoutChangingAnyBytes() throws Exception {
        JamArea area = area();
        area.append(message());
        Path header = Path.of(area.getPath() + ".jhr");
        byte[] before = Files.readAllBytes(header);
        try {
            area.create();
            fail("Existing base overwritten");
        } catch (IOException expected) {
            assertArrayEquals(before, Files.readAllBytes(header));
        }
    }

    @Test
    public void usesBaseNumberAndCanAppendAfterDeletedTail() throws Exception {
        JamArea area = area();
        try (RandomAccessFile header = new RandomAccessFile(area.getPath() + ".jhr", "rw")) {
            header.seek(20);
            header.writeInt(Integer.reverseBytes(500));
        }
        assertEquals(500, area.append(message()));
        try (RandomAccessFile index = new RandomAccessFile(area.getPath() + ".jdx", "rw")) {
            index.writeLong(-1L);
        }
        assertEquals(501, area.append(message()));
        assertEquals(501, area.read().get(0).getNumber());
    }

    @Test
    public void preventsAnotherWriterAndUpdatesOnlyRequestedAttributes() throws Exception {
        JamArea area = area();
        long number = area.append(message());
        area.locked(access -> {
            try {
                area.append(message());
                fail("Concurrent writer accepted");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("busy"));
            }
            access.attributes(number, JamArea.SENT, 0);
            return null;
        });
        assertEquals(JamArea.ECHO | JamArea.LOCAL | JamArea.SENT | 128 | 4,
                     area.read().get(0).getAttributes());
    }
}
