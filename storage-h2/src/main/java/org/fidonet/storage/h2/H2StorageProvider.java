package org.fidonet.storage.h2;

import org.fidonet.storage.StorageBackend;
import org.fidonet.storage.StorageProvider;
import org.fidonet.storage.jdbc.JdbcBackend;

import org.h2.jdbcx.JdbcConnectionPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/** H2 owns URL construction; clients supply only their existing file location. */
public final class H2StorageProvider implements StorageProvider {
    @Override
    public String id() {
        return "h2";
    }

    @Override
    public String normalizeLocation(String location) {
        return Path.of(location).toAbsolutePath().normalize().toString();
    }

    @Override
    public boolean exists(String location) {
        return Files.isRegularFile(Path.of(location + ".mv.db"));
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
        try (Connection connection = pool.getConnection();
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS storage_modules (module VARCHAR(255) PRIMARY KEY, schema_version INTEGER NOT NULL)");
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
        public int schemaVersion(String module) throws IOException {
            try (Connection connection = pool.getConnection();
                    PreparedStatement sql = connection.prepareStatement("SELECT schema_version FROM storage_modules WHERE module=?")) {
                sql.setString(1, module);
                try (ResultSet rows = sql.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            } catch (SQLException failure) {
                throw new IOException("Unable to inspect module schema version", failure);
            }
        }

        @Override
        public void recordSchemaVersion(String module, int version) throws IOException {
            try (Connection connection = pool.getConnection();
                    PreparedStatement sql = connection.prepareStatement("MERGE INTO storage_modules(module,schema_version) KEY(module) VALUES(?,?)")) {
                sql.setString(1, module);
                sql.setInt(2, version);
                sql.executeUpdate();
            } catch (SQLException failure) {
                throw new IOException("Unable to record module schema version", failure);
            }
        }

        @Override
        public void close() {
            pool.dispose();
        }
    }
}
