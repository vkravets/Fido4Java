package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.storage.UserMailRepository;
import org.fidonet.storage.RepositoryAdapter;
import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.jdbc.JdbcBackend;

import java.io.IOException;

public final class H2UserMailAdapter implements RepositoryAdapter<UserMailRepository> {
    @Override
    public Class<UserMailRepository> contract() {
        return UserMailRepository.class;
    }

    @Override
    public boolean supports(String dialect) {
        return dialect.equals("h2");
    }

    @Override
    public UserMailRepository open(StorageBackend backend) throws IOException {
        try {
            return new H2UserMailRepository(((JdbcBackend) backend).dataSource());
        } catch (Exception failure) {
            throw new IOException("Unable to initialize reader state", failure);
        }
    }
}
