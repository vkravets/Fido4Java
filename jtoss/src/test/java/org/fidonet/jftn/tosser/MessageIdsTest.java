package org.fidonet.jftn.tosser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Date;

public class MessageIdsTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

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
        Path directory = temporary.getRoot().toPath();
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
