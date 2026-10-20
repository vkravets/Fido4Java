package org.fidonet.nodelist;

import org.fidonet.storage.RepositoryAdapter;
import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.jdbc.JdbcBackend;

import java.io.IOException;

public final class H2DirectoryAdapter implements RepositoryAdapter<DirectoryRepository> {
    @Override
    public Class<DirectoryRepository> contract() {
        return DirectoryRepository.class;
    }

    @Override
    public boolean supports(String dialect) {
        return dialect.equals("h2");
    }

    @Override
    public DirectoryRepository open(StorageBackend backend) throws IOException {
        return new H2DirectoryRepository(((JdbcBackend) backend).dataSource());
    }
}
