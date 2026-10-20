package org.fidonet.fileecho.storage.h2;

import org.fidonet.fileecho.storage.FileEchoStorage;
import org.fidonet.storage.RepositoryAdapter;
import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.jdbc.JdbcBackend;

import java.io.IOException;

public final class H2FileEchoAdapter implements RepositoryAdapter<FileEchoStorage> {
    @Override
    public Class<FileEchoStorage> contract() {
        return FileEchoStorage.class;
    }

    @Override
    public boolean supports(String dialect) {
        return dialect.equals("h2");
    }

    @Override
    public FileEchoStorage open(StorageBackend backend) throws IOException {
        return new H2FileEchoStorage(((JdbcBackend) backend).dataSource());
    }
}
