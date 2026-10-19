package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.NetmailSource;
import org.fidonet.db.objects.Version;
import org.fidonet.db.robots.RobotRuleRecord;

import com.j256.ormlite.dao.Dao;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.NoSuchElementException;

public class OrmLifecycleTest {
    private String url() {
        return "jdbc:h2:mem:lifecycle" + System.nanoTime();
    }

    @Test
    public void createsDropsAndRecreatesTablesAndTracksLatestVersion() throws Exception {
        OrmManager manager = new OrmManager(url());
        assertFalse(manager.isConnect());
        manager.disconnect();
        try {
            manager.connect();
            manager.connect();
            manager.createTables();
            assertTrue(manager.isConnect());
            assertEquals(14, manager.getAllDaos().size());
            assertTrue(manager.getDao(NetmailSource.class).isTableExists());
            assertTrue(manager.getDao(RobotRuleRecord.class).isTableExists());
            assertEquals(Long.valueOf(Version.UNKNOWN_VERSION), manager.getCurrentVersion());
            Version version = new Version();
            version.setVersion(10L);
            assertTrue(manager.setCurrentVersion(version));
            version.setVersion(11L);
            assertTrue(manager.setCurrentVersion(version));
            assertEquals(Long.valueOf(11L), manager.getCurrentVersion());
            manager.dropTables();
            manager.dropTables();
            assertEquals(Long.valueOf(Version.UNKNOWN_VERSION), manager.getCurrentVersion());
            assertFalse(manager.setCurrentVersion(version));
            manager.createTables();
            assertTrue(manager.getDao(Echoarea.class).isTableExists());
        } finally {
            manager.disconnect();
            manager.disconnect();
        }
        assertFalse(manager.isConnect());
    }

    @Test
    public void optionalCredentialsAndAuthenticatedConnectionsAreSupported() throws Exception {
        for (String[] credentials : new String[][]{{null, null}, {"", ""}, {"sa", ""},
                {"sa", "password"}}) {
            OrmManager manager = new OrmManager(url(), credentials[0], credentials[1]);
            try {
                manager.connect();
                assertTrue(manager.isConnect());
            } finally {
                manager.disconnect();
            }
        }
    }

    @Test
    public void failedTableInitializationClosesConnectionAndCanBeRetried() throws Exception {
        OrmManager manager = new OrmManager(url()) {
            private boolean first = true;

            @Override
            public void createTables() throws SQLException {
                if (first) {
                    first = false;
                    throw new SQLException("create failed");
                }
                super.createTables();
            }
        };
        try {
            try {
                manager.connect();
                fail("Initialization failure ignored");
            } catch (SQLException expected) {
                assertEquals("create failed", expected.getMessage());
            }
            assertFalse(manager.isConnect());
            manager.connect();
            assertTrue(manager.getDao(Echoarea.class).isTableExists());
        } finally {
            manager.disconnect();
        }
    }

    @Test
    public void badQueryAndConversionFailuresClosePagingIterator() throws Exception {
        OrmManager manager = new OrmManager(url());
        try {
            manager.connect();
            Dao<Echoarea, Object> dao = manager.getDao(Echoarea.class);
            Echoarea area = new Echoarea();
            area.setName("test");
            dao.create(area);
            DatabasePagingIterator<Echoarea, Echoarea> iterator = new DatabasePagingIterator<>(dao.queryBuilder()) {
                @Override
                public Echoarea convert(Echoarea item) {
                    throw new IllegalStateException("conversion failed");
                }
            };
            try {
                iterator.next();
                fail("Conversion failure ignored");
            } catch (IllegalStateException expected) {
                assertEquals("conversion failed", expected.getMessage());
            }
            assertFalse(iterator.hasNext());
            iterator.close();
            manager.dropTables();
            DatabasePagingIterator<Echoarea, Echoarea> invalid = new DatabasePagingIterator<>(dao.queryBuilder());
            try {
                invalid.hasNext();
                fail("Query failure ignored");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getCause() instanceof SQLException);
            }
            assertFalse(invalid.hasNext());
            for (long[] values : new long[][]{{-2, 1}, {0, -2}}) {
                try {
                    new DatabasePagingIterator<>(dao.queryBuilder(), values[0], values[1]);
                    fail("Invalid bounds");
                } catch (IllegalArgumentException expected) {
                }
            }
            try {
                iterator.next();
                fail("Closed iterator returned a row");
            } catch (NoSuchElementException expected) {
            }
        } finally {
            manager.disconnect();
        }
    }
}
