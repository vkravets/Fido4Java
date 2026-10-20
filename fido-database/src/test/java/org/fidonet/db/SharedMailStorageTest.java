package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.echobase.MailStorage;
import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class SharedMailStorageTest {
    @TempDir
    Path root;

    @Test
    void sharedMailRepositorySurvivesClientClosureAndPersistsAcrossReopen() throws Exception {
        Path location = root.resolve("mail");
        StorageSession first = StorageServices.file(location);
        try (StorageSession second = StorageServices.file(location)) {
            MailStorage mail = first.require(MailStorage.class);
            assertSame(mail, second.require(MailStorage.class));
            first.close();
            assertTrue(mail.createArea("shared.echo"));
            assertTrue(mail.isAreaExists("shared.echo"));
        } finally {
            first.close();
        }
        try (StorageSession reopened = StorageServices.file(location)) {
            assertTrue(reopened.require(MailStorage.class).isAreaExists("shared.echo"));
        }
    }
}
