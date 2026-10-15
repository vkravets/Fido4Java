package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Date;

public class IncrementalEchoTest {
    @Test
    public void resumesAfterFailureAndKeepsConsumersIndependentAcrossRestart() throws Exception {
        String url = "jdbc:h2:mem:incremental" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseManager base = new DatabaseManager(new OrmManager(url));
        assertTrue(base.open());
        try {
            base.createArea("test");
            for (int id = 0; id < 3; id++) {
                base.addMessage(message(id), "test");
            }
            assertInterruptedScan(base);
        } finally {
            base.close();
        }
        DatabaseManager reopened = new DatabaseManager(new OrmManager(url));
        assertTrue(reopened.open());
        try {
            assertEquals(2, count(reopened, "first"));
            assertEquals(0, count(reopened, "first"));
            assertEquals(3, count(reopened, "new-link"));
            reopened.addMessage(message(3), "test");
            assertEquals(1, count(reopened, "first"));
        } finally {
            reopened.close();
        }
    }

    private void assertInterruptedScan(DatabaseManager base) throws IOException {
        int[] accepted = {0};
        try {
            base.visitNewEcho("test", "first", message -> {
                if (++accepted[0] == 2) {
                    throw new IOException("Uncommitted packet");
                }
            });
            fail("Expected interruption");
        } catch (IOException expected) {
            assertEquals("Uncommitted packet", expected.getMessage());
        }
    }

    private int count(DatabaseManager base, String consumer) throws IOException {
        int[] count = {0};
        base.visitNewEcho("test", consumer, message -> count[0]++);
        return count[0];
    }

    private Message message(int id) {
        return new Message("Author",
                           "All",
                           new FTNAddr("2:500/1"),
                           new FTNAddr("2:500/2"),
                           "Test",
                           "AREA:TEST\r\1MSGID: 2:500/1 " + id + "\rBody\r",
                           new Date(1000000));
    }
}
