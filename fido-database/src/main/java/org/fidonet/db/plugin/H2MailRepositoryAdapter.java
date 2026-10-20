package org.fidonet.db.plugin;

import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.MailStorage;
import org.fidonet.storage.RepositoryAdapter;
import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.jdbc.JdbcBackend;

import com.j256.ormlite.jdbc.db.H2DatabaseType;

import java.io.IOException;
import java.sql.SQLException;

/** ORMLite implementation remains inside the database plugin. */
public final class H2MailRepositoryAdapter implements RepositoryAdapter<MailStorage> {
    @Override
    public Class<MailStorage> contract() {
        return MailStorage.class;
    }

    @Override
    public boolean supports(String dialect) {
        return dialect.equals("h2");
    }

    @Override
    public MailStorage open(StorageBackend backend) throws IOException {
        OrmManager orm = new OrmManager(((JdbcBackend) backend).dataSource(), new H2DatabaseType());
        try {
            orm.connect();
            return new MailDatabaseFacade(orm, backend);
        } catch (SQLException failure) {
            throw new IOException("Unable to initialize mail storage", failure);
        }
    }
}
