package org.fidonet.jftn.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class PollJournalTest {
    @TempDir
    Path root;

    @Test
    void pagedUtf8EventsWithoutMixingAttemptsOrAllowingLineInjection() throws Exception {
        PollJournal first = new PollJournal(root.resolve("first.log"), 4096);
        PollJournal second = new PollJournal(root.resolve("second.log"), 4096);
        first.record("SENT", "почта.pkt\nFORGED", 123);
        first.record("STATE", "STATE_END", 0);
        second.record("STATE", "STATE_BSY", 0);
        first.flush();
        second.flush();
        PollLogSlice page = first.read(0, 1);
        assertEquals(1, page.getLines().size());
        assertTrue(page.getLines().get(0).contains("почта.pkt FORGED (123 bytes)"));
        assertTrue(page.isHasMore());
        PollLogSlice next = first.read(page.getNextOffset(), 10);
        assertEquals(1, next.getLines().size());
        assertTrue(next.getLines().get(0).contains("STATE_END"));
        assertFalse(next.isHasMore());
        assertFalse(Files.readString(root.resolve("first.log")).contains("STATE_BSY"));
        assertThrows(IllegalArgumentException.class, () -> first.read(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> first.read(0, 201));
    }

    @Test
    void boundedTheQueueAndDiskUsageAndReportedTruncation() throws Exception {
        Path path = root.resolve("bounded.log");
        PollJournal journal = new PollJournal(path, 4096);
        for (int index = 0; index < 10000; index++) {
            journal.record("SENT", "x".repeat(256), index);
        }
        journal.flush();
        long length = Files.size(path);
        assertTrue(length <= 4096);
        assertTrue(Files.readString(path).contains("limit reached"));
        journal.record("STATE", "STATE_END", 0);
        journal.flush();
        assertEquals(length, Files.size(path));
    }
}
