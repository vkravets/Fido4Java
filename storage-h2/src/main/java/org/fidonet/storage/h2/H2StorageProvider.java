package org.fidonet.storage.h2;

import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.StorageProvider;
import org.fidonet.storage.jdbc.JdbcBackend;

import org.h2.jdbcx.JdbcConnectionPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** H2 owns URL construction; clients supply only their existing file location. */
public final class H2StorageProvider implements StorageProvider {
    @Override
    public String id() {
        return "h2";
    }

    @Override
    public StorageBackend open(String location) throws IOException {
        Path path = Path.of(location).toAbsolutePath().normalize();
        if (path.toString().contains(";")) {
            throw new IllegalArgumentException("Database path cannot contain ';'");
        }
        Files.createDirectories(path.getParent());
        JdbcConnectionPool pool = JdbcConnectionPool.create("jdbc:h2:"
                                                            + path
                                                            + ";DB_CLOSE_ON_EXIT=FALSE", "", "");
        pool.setMaxConnections(8);
        pool.setLoginTimeout(10);
        try (Connection connection = pool.getConnection()) {
            connection.isValid(5);
        } catch (SQLException failure) {
            pool.dispose();
            throw new IOException("Unable to open H2 storage", failure);
        }
        return new Backend(pool);
    }

    private static final class Backend implements JdbcBackend {
        private final JdbcConnectionPool pool;

        private Backend(JdbcConnectionPool pool) {
            this.pool = pool;
        }

        @Override
        public String dialect() {
            return "h2";
        }

        @Override
        public DataSource dataSource() {
            return pool;
        }

        @Override
        public void close() {
            pool.dispose();
        }
    }
}
