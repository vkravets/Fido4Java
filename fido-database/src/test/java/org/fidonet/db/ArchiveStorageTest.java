package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.db.objects.ArchiveReceipt;
import org.fidonet.db.objects.Echomail;
import org.fidonet.db.objects.MessageReceipt;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import com.j256.ormlite.table.TableUtils;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class ArchiveStorageTest {
    private Message message() {
        return new Message("Author",
                           "All",
                           new FTNAddr("2:500/1"),
                           new FTNAddr("2:500/2"),
                           "Archive",
                           "AREA:TEST\r\1MSGID: 2:500/1 archive\rHello\r",
                           new Date());
    }

    @Test
    public void retainsArchivePolicyAndReceiptsAfterReopeningDatabase() throws Exception {
        String url = "jdbc:h2:mem:archivePolicy" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        Message message = message();
        DatabaseManager first = new DatabaseManager(new OrmManager(url));
        assertTrue(first.open());
        try {
            assertTrue(first.createArea("test"));
            assertTrue(first.importArchive(message, "/old/test"));
        } finally {
            first.close();
        }
        DatabaseManager reopened = new DatabaseManager(new OrmManager(url));
        assertTrue(reopened.open());
        try {
            assertTrue(reopened.isArchived(message));
            assertFalse(reopened.importArchive(message, "/another/copy"));
            assertEquals(1, reopened.getMessageSize("test"));
        } finally {
            reopened.close();
        }
    }

    @Test
    public void importingExistingLiveMessageDoesNotDisableItsDistribution() throws Exception {
        DatabaseManager base = new DatabaseManager(new OrmManager("jdbc:h2:mem:liveArchive"
                                                                  + System.nanoTime()));
        assertTrue(base.open());
        try {
            assertTrue(base.createArea("test"));
            Message message = message();
            base.addMessage(message, "test");
            assertFalse(base.importArchive(message, "/old/test"));
            assertFalse(base.isArchived(message));
            assertEquals(1, base.getMessageSize("test"));
        } finally {
            base.close();
        }
    }

    @Test
    public void failedProvenanceWriteRollsBackMessageAndDupeReceipt() throws Exception {
        OrmManager orm = new OrmManager("jdbc:h2:mem:archiveRollback" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            assertTrue(base.createArea("test"));
            TableUtils.dropTable(orm.connectionSource, ArchiveReceipt.class, true);
            try {
                base.importArchive(message(), "/old/test");
                fail("Missing provenance table accepted");
            } catch (IllegalStateException expected) {
                assertEquals(0, orm.getDao(Echomail.class).countOf());
                assertEquals(0, orm.getDao(MessageReceipt.class).countOf());
            }
        } finally {
            base.close();
        }
    }
}
