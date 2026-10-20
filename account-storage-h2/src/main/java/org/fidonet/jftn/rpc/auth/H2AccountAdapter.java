package org.fidonet.jftn.rpc.auth;

import org.fidonet.storage.RepositoryAdapter;
import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.jdbc.JdbcBackend;

import java.io.IOException;

public final class H2AccountAdapter implements RepositoryAdapter<AccountRepository> {
    @Override
    public Class<AccountRepository> contract() {
        return AccountRepository.class;
    }

    @Override
    public boolean supports(String dialect) {
        return dialect.equals("h2");
    }

    @Override
    public AccountRepository open(StorageBackend backend) throws IOException {
        return new H2AccountRepository(((JdbcBackend) backend).dataSource());
    }
}
