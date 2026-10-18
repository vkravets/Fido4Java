package org.fidonet.fileecho;

import org.fidonet.db.MailDatabaseFacade;

import com.j256.ormlite.dao.Dao;

import java.io.IOException;
import java.sql.SQLException;

/** SQL access and transaction boundary, without fileecho authorization decisions. */
final class FileEchoRepository {
    @FunctionalInterface
    interface Work<T> {
        T run() throws SQLException;
    }

    final Dao<FileAreaRecord, String> areas;
    final Dao<FileAccessRecord, String> access;
    final Dao<FileCreationRecord, String> policies;
    private final MailDatabaseFacade database;

    FileEchoRepository(MailDatabaseFacade database) throws IOException {
        this.database = database;
        try {
            areas = database.extensionDao(FileAreaRecord.class);
            access = database.extensionDao(FileAccessRecord.class);
            policies = database.extensionDao(FileCreationRecord.class);
        } catch (SQLException failure) {
            throw new IOException("Cannot initialize fileecho storage", failure);
        }
    }

    <T> T transaction(Work<T> work) throws SQLException {
        return database.extensionTransaction(work::run);
    }

    <T> T execute(Work<T> work) throws IOException {
        try {
            return work.run();
        } catch (SQLException failure) {
            throw new IOException("Cannot access fileecho storage", failure);
        }
    }
}
