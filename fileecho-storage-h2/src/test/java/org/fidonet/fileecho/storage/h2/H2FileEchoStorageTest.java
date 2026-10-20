package org.fidonet.fileecho.storage.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.fileecho.storage.FileArea;
import org.fidonet.fileecho.storage.FileCreationPolicy;
import org.fidonet.fileecho.storage.FileEchoStorage;
import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;
import org.fidonet.storage.h2.H2StorageProvider;
import org.fidonet.storage.jdbc.JdbcBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

class H2FileEchoStorageTest {
    @TempDir
    Path root;
    private static final String PEER = "1:500/101.0";

    @Test
    void persistsTypedOperationsWithoutReplacingConfiguredAreaEdits() throws Exception {
        StorageServices services = new StorageServices();
        String location = root.resolve("fileecho").toString();
        try (StorageSession session = services.open("h2", location)) {
            FileEchoStorage storage = session.require(FileEchoStorage.class);
            storage.seedAreas(List.of(area("lists")));
            storage.saveArea(new FileArea("lists", "edited", "Disabled", false));
            storage.seedAreas(List.of(area("lists"), area("other")));
            assertFalse(storage.area("lists").enabled);
            assertEquals("edited", storage.area("lists").directory);
            storage.saveAccess("lists", PEER, "WRITE");
            storage.subscribe("lists", PEER, true);
            assertEquals("BOTH", storage.access("lists", PEER).access);
            storage.subscribe("lists", PEER, true);
            assertEquals(1, storage.access(PEER).size());
            storage.subscribe("other", PEER, true);
            assertEquals("READ", storage.access("other", PEER).access);
            storage.subscribe("other", PEER, false);
            assertNull(storage.access("other", PEER));
            storage.savePolicy(PEER, "lists.*", "WRITE");
            storage.createWithGrant(area("lists.nodes"), storage.policy(PEER));
            assertEquals("WRITE", storage.access("lists.nodes", PEER).access);
        }
        try (StorageSession session = services.open("h2", location)) {
            FileEchoStorage storage = session.require(FileEchoStorage.class);
            assertEquals(3, storage.areas().size());
            assertEquals("Disabled", storage.area("lists").description);
            assertEquals(2, storage.access().size());
            assertEquals(1, storage.policies().size());
            storage.saveAccess("lists", PEER, "NONE");
            storage.savePolicy(PEER, "", "NONE");
            assertNull(storage.policy(PEER));
            assertNull(storage.access("lists", PEER));
        }
    }

    @Test
    void rejectsChangedPolicyAndNeverGrantsAccessToExistingArea() throws Exception {
        try (StorageSession session = StorageServices.file(root.resolve("policy"))) {
            FileEchoStorage storage = session.require(FileEchoStorage.class);
            storage.savePolicy(PEER, "lists.*", "WRITE");
            FileCreationPolicy previous = storage.policy(PEER);
            storage.savePolicy(PEER, "private.*", "BOTH");
            assertThrows(IOException.class,
                         () -> storage.createWithGrant(area("lists.nodes"), previous));
            assertTrue(storage.areas().isEmpty());
            assertTrue(storage.access().isEmpty());
            storage.saveArea(area("lists.nodes"));
            assertNotNull(storage.createWithGrant(area("lists.nodes"), previous));
            assertTrue(storage.access().isEmpty());
        }
    }

    @Test
    void rollsBackAreaWhenItsFirstGrantCannotBeWritten() throws Exception {
        try (StorageBackend backend = new H2StorageProvider().open(root.resolve("rollback")
                                                                       .toString());
                FileEchoStorage storage = new H2FileEchoAdapter().open(backend)) {
            storage.savePolicy(PEER, "*", "WRITE");
            try (Connection connection = ((JdbcBackend) backend).dataSource().getConnection();
                    Statement sql = connection.createStatement()) {
                sql.execute("ALTER TABLE \"fileecho_access\" ADD CONSTRAINT reject_write CHECK (\"access\" <> 'WRITE')");
            }
            assertThrows(IOException.class,
                         () -> storage.createWithGrant(area("lists"), storage.policy(PEER)));
            assertNull(storage.area("lists"));
            assertTrue(storage.access().isEmpty());
            assertNotNull(storage.policy(PEER));
        }
    }

    @Test
    void adoptsOriginalQuotedTablesAndKeepsProviderSourceAlive() throws Exception {
        try (StorageBackend backend = new H2StorageProvider().open(root.resolve("old")
                                                                       .toString())) {
            JdbcBackend jdbc = (JdbcBackend) backend;
            createOldTables(jdbc);
            try (FileEchoStorage storage = new H2FileEchoAdapter().open(backend)) {
                assertFalse(storage.area("lists").enabled);
                assertEquals("old directory", storage.area("lists").directory);
                assertEquals("BOTH", storage.access("lists", PEER).access);
                assertEquals("lists.*", storage.policy(PEER).masks);
                storage.saveAccess("lists", PEER, "READ");
            }
            try (FileEchoStorage reopened = new H2FileEchoAdapter().open(backend)) {
                assertEquals("READ", reopened.access("lists", PEER).access);
            }
            try (Connection connection = jdbc.dataSource().getConnection()) {
                assertFalse(connection.isClosed());
            }
        }
    }

    private static void createOldTables(JdbcBackend backend) throws Exception {
        // Frozen original ORMLite schema, including its quoted lowercase identifiers and IDs.
        try (Connection connection = backend.dataSource().getConnection();
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE \"fileecho_area\" (\"name\" VARCHAR(255) PRIMARY KEY, \"directory\" VARCHAR(4096) NOT NULL, \"description\" VARCHAR(1024) NOT NULL, \"enabled\" BOOLEAN)");
            sql.execute("CREATE TABLE \"fileecho_access\" (\"id\" VARCHAR(255) PRIMARY KEY, \"area\" VARCHAR(255) NOT NULL, \"peer\" VARCHAR(255) NOT NULL, \"access\" VARCHAR(255) NOT NULL)");
            sql.execute("CREATE TABLE \"fileecho_creation\" (\"peer\" VARCHAR(255) PRIMARY KEY, \"masks\" VARCHAR(1024) NOT NULL, \"access\" VARCHAR(255) NOT NULL)");
            sql.execute("INSERT INTO \"fileecho_area\" VALUES ('lists', 'old directory', 'Original', false)");
            sql.execute("INSERT INTO \"fileecho_access\" VALUES ('lists:1:500/101.0', 'lists', '1:500/101.0', 'BOTH')");
            sql.execute("INSERT INTO \"fileecho_creation\" VALUES ('1:500/101.0', 'lists.*', 'WRITE')");
        }
    }

    @Test
    void sharesRepositoryAcrossLeasesAndClosesOnlyAfterLastOwner() throws Exception {
        StorageServices services = new StorageServices();
        String location = root.resolve("shared").toString();
        FileEchoStorage repository;
        try (StorageSession owner = services.open("h2", location)) {
            repository = owner.require(FileEchoStorage.class);
            try (StorageSession other = services.open("h2", location)) {
                assertSame(repository, other.require(FileEchoStorage.class));
            }
            repository.saveArea(area("lists"));
        }
        assertThrows(IOException.class, repository::areas);
    }

    private static FileArea area(String name) {
        return new FileArea(name, "fileecho/" + name, "Lists", true);
    }
}
