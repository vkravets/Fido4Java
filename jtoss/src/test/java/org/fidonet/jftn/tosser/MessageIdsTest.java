package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class MessageIdsTest {
    @TempDir
    public File temporary;

    private Message message() {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("1:500/1"),
                           new FTNAddr("1:500/2"),
                           "Subject",
                           "Body\r",
                           new Date());
    }

    @Test
    public void preservesExistingIdsAndAllocatesIncreasingSerialsAcrossReopen() throws Exception {
        Path directory = temporary.toPath();
        Message first = message();
        MessageIds.ensure(first, directory);
        String id = first.getMsgId();
        assertTrue(id.matches("1:500/1 [0-9a-f]{8}"));
        MessageIds.ensure(first, directory);
        assertEquals(id, first.getMsgId());
        Message second = message();
        MessageIds.ensure(second, directory);
        long before = Long.parseLong(id.split(" ")[1], 16);
        long after = Long.parseLong(second.getMsgId().split(" ")[1], 16);
        assertTrue(after > before);
        assertTrue(second.getBody().toString().contains("\1MSGID: " + second.getMsgId() + "\r"));
    }

    @Test
    void retainsPointNumbersAndForeignIdsWithoutAllocatingAnotherSerial() throws Exception {
        Message point = new Message("Author",
                                    "Reader",
                                    new FTNAddr("1:500/1.7"),
                                    new FTNAddr("1:500/2"),
                                    "Subject",
                                    "Body\r",
                                    new Date());
        MessageIds.ensure(point, temporary.toPath());
        assertTrue(point.getMsgId().matches("1:500/1\\.7 [0-9a-f]{8}"));
        byte[] sequence = Files.readAllBytes(temporary.toPath().resolve("msgid.sequence"));
        Message foreign = message();
        foreign.setMsgId("1:500/99 old-editor-id");
        MessageIds.ensure(foreign, temporary.toPath());
        assertEquals("1:500/99 old-editor-id", foreign.getMsgId());
        assertEquals(ByteBuffer.wrap(sequence).getLong(),
                     ByteBuffer.wrap(Files.readAllBytes(temporary.toPath()
                                                                 .resolve("msgid.sequence")))
                               .getLong());
    }

    @Test
    void continuesThePersistentSequenceWhenItIsAheadOfTheClock() throws Exception {
        long previous = System.currentTimeMillis() / 1000 + 10000;
        Files.write(temporary.toPath().resolve("msgid.sequence"),
                    ByteBuffer.allocate(8).putLong(previous).array());
        Message next = message();
        MessageIds.ensure(next, temporary.toPath());
        assertEquals(previous + 1, Long.parseLong(next.getMsgId().split(" ")[1], 16));
    }
}
