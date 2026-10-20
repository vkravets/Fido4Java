package org.fidonet.storage.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.jdbc.JdbcBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

class H2StorageProviderTest {
    @TempDir
    Path root;

    @Test
    void concurrentBorrowersHaveIndependentTransactionsAndClosingOneKeepsOthersUsable()
            throws Exception {
        H2StorageProvider provider = new H2StorageProvider();
        try (StorageBackend backend = provider.open(root.resolve("isolation").toString())) {
            JdbcBackend jdbc = (JdbcBackend) backend;
            try (Connection first = jdbc.dataSource().getConnection();
                    Statement sql = first.createStatement()) {
                sql.execute("CREATE TABLE payload (id INTEGER PRIMARY KEY, value_text VARCHAR)");
                sql.execute("INSERT INTO payload VALUES (1, 'original')");
                first.setAutoCommit(false);
                sql.execute("UPDATE payload SET value_text='pending' WHERE id=1");
                try (Connection second = jdbc.dataSource().getConnection();
                        Statement query = second.createStatement();
                        ResultSet rows = query.executeQuery("SELECT value_text FROM payload")) {
                    assertTrue(rows.next());
                    assertEquals("original", rows.getString(1));
                }
                first.rollback();
                try (ResultSet rows = sql.executeQuery("SELECT value_text FROM payload")) {
                    assertTrue(rows.next());
                    assertEquals("original", rows.getString(1));
                }
            }
        }
    }

    @Test
    void schemaMetadataPersistsAndLocationsCannotInjectJdbcOptions() throws Exception {
        H2StorageProvider provider = new H2StorageProvider();
        String location = root.resolve("versions").toString();
        try (StorageBackend backend = provider.open(location)) {
            assertEquals(0, backend.schemaVersion("example"));
            backend.recordSchemaVersion("example", 3);
        }
        try (StorageBackend backend = provider.open(location)) {
            assertEquals(3, backend.schemaVersion("example"));
        }
        assertThrows(IllegalArgumentException.class,
                     () -> provider.open(location + ";INIT=RUNSCRIPT FROM 'unexpected.sql'"));
    }
}
