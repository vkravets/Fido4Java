package org.fidonet.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Subscription;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;

public class DatabaseOperationsTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private OrmManager database;
    private DatabaseManager manager;

    @Before
    public void open() {
        database = new OrmManager("jdbc:h2:mem:operations" + System.nanoTime());
        manager = new DatabaseManager(database);
        assertTrue(manager.open());
    }

    @After
    public void close() {
        manager.close();
    }

    @Test
    public void openingTwicePreservesAreasAndDuplicateCreationDoesNotOverwriteThem()
            throws Exception {
        assertTrue(manager.createArea("one", "Original description"));
        assertTrue(manager.open());
        assertFalse(manager.createArea("one", "Replacement"));
        assertTrue(manager.isAreaExists("one"));
        assertFalse(manager.isAreaExists("missing"));
        assertEquals("Original description",
                     database.getDao(Echoarea.class)
                             .queryForEq("name", "one")
                             .get(0)
                             .getDescription());
        assertEquals(Collections.singletonList("one"), manager.getAreas());
        assertEquals(0, manager.getMessageSize("one"));
        assertEquals(-1, manager.getMessageSize("missing"));
    }

    @Test
    public void messageIdsAreScopedToAreaAndGlobalLookupReturnsBothCopies() {
        Message first = message("one", "shared", "first");
        Message second = message("two", "shared", "second");
        manager.addMessage(first, "one");
        manager.addMessage(second, "two");
        assertEquals("first", manager.getMessage("one", first.getMsgId()).getSubject());
        assertEquals("second", manager.getMessage("two", second.getMsgId()).getSubject());
        assertEquals(2, manager.getMessage(first.getMsgId()).size());
        assertEquals(1, manager.getMessageSize("one"));
        assertTrue(manager.isDupe(first));
        assertFalse(manager.isDupe(message("missing", "shared", "absent area")));
        assertFalse(manager.isDupe(message("one", "other", "different id")));
        assertNull(manager.getMessage("missing", first.getMsgId()));
        assertNull(manager.getMessage("one", "unknown"));
        assertTrue(manager.getMessage("unknown").isEmpty());
        assertFalse(manager.getMessages("missing").hasNext());
    }

    @Test
    public void missingLinksAndAreasDoNotCreateSubscriptions() throws Exception {
        assertTrue(manager.createArea("one"));
        Link unknown = new Link("1:500/999,1:500/100,secret,localhost");
        manager.addSubscription(unknown, "one");
        manager.setLinkLastMessage(unknown, "one", 100L);
        assertFalse(manager.getMessages(unknown).hasNext());
        Link known = configuredLink();
        manager.addSubscription(known, "missing");
        manager.setLinkLastMessage(known, "missing", 100L);
        manager.setLinkLastMessage(known, "one", 100L);
        assertEquals(0, database.getDao(Subscription.class).countOf());
        assertFalse(manager.getMessages(known).hasNext());
        manager.addSubscription(known, "one");
        manager.addSubscription(known, "one");
        assertEquals(1, database.getDao(Subscription.class).countOf());
        assertEquals(Collections.singletonList("one"), manager.getSubscriptions(known));
        manager.setLinkLastMessage(known, "one", 100L);
        Subscription subscription = database.<Subscription, Object>getDao(Subscription.class)
                                            .queryForAll()
                                            .get(0);
        assertEquals(Long.valueOf(100), subscription.getLastMessage());
        assertFalse(manager.getMessages(known, "one", 0, 1).hasNext());
    }

    @Test
    public void unavailableTablesDoNotReturnMessagesOrReportSuccessfulWrites() throws Exception {
        Link link = configuredLink();
        database.dropTables();
        assertFalse(manager.createArea("one"));
        assertFalse(manager.isAreaExists("one"));
        assertTrue(manager.getAreas().isEmpty());
        assertNull(manager.getMessage("one", "unknown"));
        assertNull(manager.getMessage("unknown"));
        assertEquals(0, manager.getMessageSize("one"));
        try {
            manager.isDupe(message("one", "id", "test"));
            fail("Duplicate-check failure must be reported");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getCause() instanceof SQLException);
        }
        assertTrue(manager.getSubscriptions(link).isEmpty());
        assertFalse(manager.getMessages(link).hasNext());
        assertFalse(manager.getMessages("one").hasNext());
        try {
            manager.addMessage(message("one", "id", "test"), "one");
            fail("Write failure must be reported");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getCause() instanceof SQLException);
        }
        manager.addSubscription(link, "one");
        manager.setLinkLastMessage(link, "one", 10L);
        database.createTables();
        assertTrue(manager.getAreas().isEmpty());
    }

    @Test
    public void invalidDatabaseDriverReportsOpenFailure() {
        OrmManager invalid = new OrmManager("jdbc:unavailable:coverage");
        DatabaseManager failed = new DatabaseManager(invalid);
        try {
            assertFalse(failed.open());
            assertFalse(invalid.isConnect());
        } finally {
            failed.close();
        }
    }

    @Test
    public void inaccessibleDatabasePathReportsOpenFailure() throws Exception {
        File blocked = temporary.newFile("parent-is-file");
        OrmManager invalid = new OrmManager("jdbc:h2:"
                                            + new File(blocked, "database").getAbsolutePath());
        DatabaseManager failed = new DatabaseManager(invalid);
        try {
            assertFalse(failed.open());
            assertFalse(invalid.isConnect());
        } finally {
            failed.close();
        }
        assertTrue(blocked.isFile());
    }

    @Test
    public void failedTransactionRollsBackAreaAndNextTransactionCanSucceed() throws Exception {
        try {
            database.inTransaction(() -> {
                assertTrue(manager.createArea("rolled-back"));
                throw new SQLException("Abort transaction");
            });
            fail("Failed transaction must report its error");
        } catch (SQLException expected) {
            assertEquals("Abort transaction", expected.getMessage());
        }
        assertFalse(manager.isAreaExists("rolled-back"));
        assertTrue(database.inTransaction(() -> manager.createArea("committed")));
        assertTrue(manager.isAreaExists("committed"));
    }

    private Link configuredLink() throws Exception {
        ConfigurationLink configuration = new ConfigurationLink();
        configuration.setAddress("1:500/101.0@fidonet.org");
        configuration.setPassword("secret");
        configuration.setPacket_password("secret");
        database.getDao(ConfigurationLink.class).create(configuration);
        return configuration.toLink();
    }

    private static Message message(String area, String id, String subject) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("1:500/100"),
                           new FTNAddr("1:500/101"),
                           subject,
                           "AREA:" + area + "\r\1MSGID: 1:500/100 " + id + "\rBody\r",
                           new Date(1700000000000L));
    }
}
