package org.fidonet.fileecho.storage.h2;

import org.fidonet.fileecho.storage.FileAccess;
import org.fidonet.fileecho.storage.FileArea;
import org.fidonet.fileecho.storage.FileCreationPolicy;
import org.fidonet.fileecho.storage.FileEchoStorage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;

/** H2 queries over the provider-owned source; retains the original ORMLite table layout. */
public final class H2FileEchoStorage implements FileEchoStorage {
    private final DataSource source;
    private volatile boolean closed;

    public H2FileEchoStorage(DataSource source) throws IOException {
        this.source = source;
        transaction(connection -> {
            execute(connection,
                    "CREATE TABLE IF NOT EXISTS `fileecho_area` (`name` VARCHAR(255) PRIMARY KEY, `directory` VARCHAR(4096) NOT NULL, `description` VARCHAR(1024) NOT NULL, `enabled` BOOLEAN)");
            execute(connection,
                    "CREATE TABLE IF NOT EXISTS `fileecho_access` (`id` VARCHAR(255) PRIMARY KEY, `area` VARCHAR(255) NOT NULL, `peer` VARCHAR(255) NOT NULL, `access` VARCHAR(255) NOT NULL)");
            execute(connection,
                    "CREATE TABLE IF NOT EXISTS `fileecho_creation` (`peer` VARCHAR(255) PRIMARY KEY, `masks` VARCHAR(1024) NOT NULL, `access` VARCHAR(255) NOT NULL)");
            return null;
        });
    }

    @Override
    public List<FileArea> areas() throws IOException {
        return read(connection -> query(connection,
                                        "SELECT * FROM `fileecho_area` ORDER BY `name`",
                                        H2FileEchoStorage::area));
    }

    @Override
    public FileArea area(String name) throws IOException {
        return read(connection -> area(connection, name));
    }

    private static FileArea area(Connection connection, String name) throws SQLException {
        return first(query(connection,
                           "SELECT * FROM `fileecho_area` WHERE `name`=?",
                           H2FileEchoStorage::area,
                           name));
    }

    private static FileArea area(ResultSet row) throws SQLException {
        return new FileArea(row.getString("name"),
                            row.getString("directory"),
                            row.getString("description"),
                            row.getBoolean("enabled"));
    }

    @Override
    public List<FileAccess> access() throws IOException {
        return read(connection -> query(connection,
                                        "SELECT * FROM `fileecho_access` ORDER BY `id`",
                                        H2FileEchoStorage::access));
    }

    @Override
    public List<FileAccess> access(String peer) throws IOException {
        return read(connection -> query(connection,
                                        "SELECT * FROM `fileecho_access` WHERE `peer`=? ORDER BY `area`",
                                        H2FileEchoStorage::access,
                                        peer));
    }

    @Override
    public FileAccess access(String area, String peer) throws IOException {
        return read(connection -> access(connection, area, peer));
    }

    private static FileAccess access(Connection connection, String area, String peer)
            throws SQLException {
        return first(query(connection,
                           "SELECT * FROM `fileecho_access` WHERE `id`=?",
                           H2FileEchoStorage::access,
                           area + ":" + peer));
    }

    private static FileAccess access(ResultSet row) throws SQLException {
        return new FileAccess(row.getString("area"),
                              row.getString("peer"),
                              row.getString("access"));
    }

    @Override
    public List<FileCreationPolicy> policies() throws IOException {
        return read(connection -> query(connection,
                                        "SELECT * FROM `fileecho_creation` ORDER BY `peer`",
                                        H2FileEchoStorage::policy));
    }

    @Override
    public FileCreationPolicy policy(String peer) throws IOException {
        return read(connection -> policy(connection, peer, false));
    }

    private static FileCreationPolicy policy(Connection connection, String peer, boolean lock)
            throws SQLException {
        return first(query(connection,
                           "SELECT * FROM `fileecho_creation` WHERE `peer`=?"
                           + (lock ? " FOR UPDATE" : ""),
                           H2FileEchoStorage::policy,
                           peer));
    }

    private static FileCreationPolicy policy(ResultSet row) throws SQLException {
        return new FileCreationPolicy(row.getString("peer"),
                                      row.getString("masks"),
                                      row.getString("access"));
    }

    @Override
    public synchronized void seedAreas(Collection<FileArea> areas) throws IOException {
        transaction(connection -> {
            for (FileArea area : areas) {
                if (area(connection, area.name) == null) {
                    saveArea(connection, area);
                }
            }
            return null;
        });
    }

    @Override
    public synchronized void saveArea(FileArea area) throws IOException {
        transaction(connection -> {
            saveArea(connection, area);
            return null;
        });
    }

    private static void saveArea(Connection connection, FileArea area) throws SQLException {
        execute(connection,
                "MERGE INTO `fileecho_area` (`name`,`directory`,`description`,`enabled`) KEY(`name`) VALUES (?,?,?,?)",
                area.name,
                area.directory,
                area.description,
                area.enabled);
    }

    @Override
    public synchronized void saveAccess(String area, String peer, String access)
            throws IOException {
        transaction(connection -> {
            saveAccess(connection, area, peer, access);
            return null;
        });
    }

    private static void saveAccess(Connection connection, String area, String peer, String access)
            throws SQLException {
        String id = area + ":" + peer;
        if (access.equals("NONE")) {
            execute(connection, "DELETE FROM `fileecho_access` WHERE `id`=?", id);
            return;
        }
        execute(connection,
                "MERGE INTO `fileecho_access` (`id`,`area`,`peer`,`access`) KEY(`id`) VALUES (?,?,?,?)",
                id,
                area,
                peer,
                access);
    }

    @Override
    public synchronized void savePolicy(String peer, String masks, String access)
            throws IOException {
        transaction(connection -> {
            if (access.equals("NONE")) {
                execute(connection, "DELETE FROM `fileecho_creation` WHERE `peer`=?", peer);
            } else {
                execute(connection,
                        "MERGE INTO `fileecho_creation` (`peer`,`masks`,`access`) KEY(`peer`) VALUES (?,?,?)",
                        peer,
                        masks,
                        access);
            }
            return null;
        });
    }

    @Override
    public synchronized void subscribe(String area, String peer, boolean enabled)
            throws IOException {
        transaction(connection -> {
            FileAccess previous = access(connection, area, peer);
            if (!enabled) {
                saveAccess(connection, area, peer, "NONE");
            } else if (previous == null || previous.access.equals("WRITE")) {
                saveAccess(connection, area, peer, previous == null ? "READ" : "BOTH");
            }
            return null;
        });
    }

    @Override
    public synchronized FileArea createWithGrant(FileArea area, FileCreationPolicy expected)
            throws IOException {
        return transaction(connection -> {
            FileArea previous = area(connection, area.name);
            if (previous != null) {
                return previous;
            }
            if (expected == null) {
                throw new IOException("File creation policy is missing");
            }
            FileCreationPolicy current = policy(connection, expected.peer, true);
            if (current == null || !current.masks.equals(expected.masks)
                    || !current.access.equals(expected.access)) {
                throw new IOException("File creation policy changed");
            }
            saveArea(connection, area);
            saveAccess(connection, area.name, current.peer, current.access);
            return area;
        });
    }

    private Connection connect() throws SQLException {
        if (closed) {
            throw new SQLException("FileEcho storage has been closed");
        }
        return source.getConnection();
    }

    private <T> T read(Work<T> operation) throws IOException {
        try (Connection connection = connect()) {
            return operation.run(connection);
        } catch (SQLException failure) {
            throw new IOException("Cannot access FileEcho storage", failure);
        }
    }

    private <T> T transaction(Work<T> operation) throws IOException {
        return read(connection -> {
            connection.setAutoCommit(false);
            try {
                T result = operation.run(connection);
                connection.commit();
                return result;
            } catch (IOException | SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        });
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollback) {
            failure.addSuppressed(rollback);
        }
    }

    private static void execute(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            statement.executeUpdate();
        }
    }

    private static <T> List<T> query(Connection connection,
                                     String sql,
                                     Row<T> mapper,
                                     Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters);
                ResultSet rows = statement.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rows.next()) {
                result.add(mapper.read(rows));
            }
            return List.copyOf(result);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object[] parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql.replace('`', '"'));
        try {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        } catch (SQLException | RuntimeException failure) {
            statement.close();
            throw failure;
        }
    }

    private static <T> T first(List<T> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void close() {
        closed = true;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException, IOException;
    }

    @FunctionalInterface
    private interface Row<T> {
        T read(ResultSet row) throws SQLException;
    }
}
