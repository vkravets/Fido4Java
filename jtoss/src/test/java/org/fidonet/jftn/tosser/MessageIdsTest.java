package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;

public class MessageIdsTest {
    @TempDir
    public File temporary;

    private Message message() {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("2:500/1"),
                           new FTNAddr("2:500/2"),
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
        MessageIds.ensure(first, directory);
        assertEquals(id, first.getMsgId());
        Message second = message();
        MessageIds.ensure(second, directory);
        long before = Long.parseLong(id.split(" ")[1], 16);
        long after = Long.parseLong(second.getMsgId().split(" ")[1], 16);
        assertTrue(after > before);
        assertTrue(second.getBody().toString().contains("\1MSGID: " + second.getMsgId() + "\r"));
    }
}
