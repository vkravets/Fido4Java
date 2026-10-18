package org.fidonet.nodelist;

import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Transactional source replacement, bounded streaming inserts and indexed address lookup. */
public final class DirectoryCatalog implements AutoCloseable {
    private static final String COLUMNS = "source_id,address,keyword,system_name,location,sysop,phone,speed,region,hub,flags,line_no";
    private final String jdbcUrl;
    private volatile boolean closed;

    public DirectoryCatalog(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.toString().contains(";")) {
            throw new IllegalArgumentException("Directory database path cannot contain ';'");
        }
        Files.createDirectories(absolute.getParent());
        jdbcUrl = "jdbc:h2:" + absolute;
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS directory_sources (source_id VARCHAR(80) PRIMARY KEY, definition CLOB NOT NULL, fingerprint VARCHAR(64) NOT NULL, record_count BIGINT NOT NULL, header CLOB NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS directory_entries (source_id VARCHAR(80) NOT NULL, address VARCHAR(40) NOT NULL, keyword VARCHAR(64) NOT NULL, system_name CLOB NOT NULL, location CLOB NOT NULL, sysop CLOB NOT NULL, phone CLOB NOT NULL, speed VARCHAR(255) NOT NULL, region INTEGER, hub INTEGER, flags CLOB NOT NULL, line_no BIGINT NOT NULL, PRIMARY KEY(source_id,address))");
            statement.execute("CREATE INDEX IF NOT EXISTS directory_address_idx ON directory_entries(address)");
        } catch (SQLException e) {
            throw new IOException("Unable to open node directory " + absolute, e);
        }
    }

    private Connection connect() throws SQLException {
        if (closed) {
            throw new SQLException("Directory catalog has been closed");
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    public synchronized DirectoryImportResult importSource(DirectorySource source)
            throws IOException {
        String fingerprint = fingerprint(source.getPath());
        try (Connection connection = connect()) {
            DirectoryImportResult old = previous(connection, source, fingerprint);
            if (old != null) {
                return old;
            }
            connection.setAutoCommit(false);
            try {
                DirectoryImportResult result = replace(connection, source, fingerprint);
                connection.commit();
                return result;
            } catch (IOException | SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } catch (SQLException e) {
            throw new IOException("Unable to import directory source " + source.getId(), e);
        }
    }

    /** Validate parsing and SQL uniqueness using bounded inserts, then roll back all changes. */
    public synchronized void validateSource(DirectorySource source) throws IOException {
        String fingerprint = fingerprint(source.getPath());
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                replace(connection, source, fingerprint);
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IOException("Directory validation failed for " + source.getId(), e);
        }
    }

    private DirectoryImportResult previous(Connection connection,
                                           DirectorySource source,
                                           String fingerprint)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT definition,fingerprint,record_count,header FROM directory_sources WHERE source_id=?")) {
            statement.setString(1, source.getId());
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next() && source.definition().equals(rows.getString(1))
                        && fingerprint.equals(rows.getString(2))) {
                    return new DirectoryImportResult(source.getId(),
                                                     rows.getLong(3),
                                                     false,
                                                     fingerprint,
                                                     rows.getString(4));
                }
                return null;
            }
        }
    }

    private DirectoryImportResult replace(Connection connection,
                                          DirectorySource source,
                                          String fingerprint)
            throws SQLException, IOException {
        delete(connection, "directory_entries", source.getId());
        MessageDigest digest = sha256();
        DirectoryParser.Result parsed;
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO directory_entries ("
                + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
                InputStream input = new DigestInputStream(limited(source.getPath()), digest);
                DirectoryInput decoded = DirectoryInput.open(input)) {
            EntryBatch batch = new EntryBatch(statement, source.getId());
            parsed = DirectoryParser.parse(decoded.content(), source, batch::add);
            statement.executeBatch();
            decoded.finish();
            drain(input);
        }
        if (!fingerprint.equals(hex(digest.digest()))) {
            throw new IOException("Directory source changed while importing: " + source.getPath());
        }
        delete(connection, "directory_sources", source.getId());
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO directory_sources VALUES (?,?,?,?,?)")) {
            statement.setString(1, source.getId());
            statement.setString(2, source.definition());
            statement.setString(3, fingerprint);
            statement.setLong(4, parsed.getRecords());
            statement.setString(5, parsed.getHeader());
            statement.executeUpdate();
        }
        return new DirectoryImportResult(source.getId(),
                                         parsed.getRecords(),
                                         true,
                                         fingerprint,
                                         parsed.getHeader());
    }

    private static final class EntryBatch {
        private final PreparedStatement statement;
        private final String source;
        private int pending;

        private EntryBatch(PreparedStatement statement, String source) {
            this.statement = statement;
            this.source = source;
        }

        private void add(DirectoryEntry entry) throws IOException {
            try {
                append(entry);
            } catch (SQLException failure) {
                throw new IOException(source
                                      + ":"
                                      + entry.getLine()
                                      + ": Unable to store directory record", failure);
            }
        }

        private void append(DirectoryEntry entry) throws SQLException {
            bind(statement, entry);
            statement.addBatch();
            pending++;
            if (pending == 500) {
                statement.executeBatch();
                pending = 0;
            }
        }
    }

    private static void bind(PreparedStatement statement, DirectoryEntry entry)
            throws SQLException {
        statement.setString(1, entry.getSource());
        statement.setString(2, entry.addressKey());
        statement.setString(3, entry.getKeyword());
        statement.setString(4, entry.getSystem());
        statement.setString(5, entry.getLocation());
        statement.setString(6, entry.getSysop());
        statement.setString(7, entry.getPhone());
        statement.setString(8, entry.getSpeed());
        statement.setObject(9, entry.getRegion());
        statement.setObject(10, entry.getHub());
        statement.setString(11, String.join(",", entry.getFlags()));
        statement.setLong(12, entry.getLine());
    }

    public Optional<DirectoryEntry> lookup(String source, FTNAddr address) throws IOException {
        List<DirectoryEntry> entries = query("SELECT " + COLUMNS
                + " FROM directory_entries WHERE source_id=? AND address=?",
                                             source,
                                             address.toString());
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }

    /** Returns all source records for an address; no implicit cross-list priority. */
    public List<DirectoryEntry> lookup(FTNAddr address) throws IOException {
        return query("SELECT " + COLUMNS
                + " FROM directory_entries WHERE address=? ORDER BY source_id", address.toString());
    }

    /** Literal, case-insensitive name search; paging is required and capped at 500 records. */
    public List<DirectoryEntry> search(String source, String name, int offset, int limit)
            throws IOException {
        if (offset < 0 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Search needs offset >= 0 and limit 1..500");
        }
        String pattern = "%"
                         + name.toLowerCase(Locale.ROOT)
                               .replace("!", "!!")
                               .replace("%", "!%")
                               .replace("_", "!_")
                         + "%";
        return query("SELECT " + COLUMNS
                + " FROM directory_entries WHERE source_id=? AND (LOWER(sysop) LIKE ? ESCAPE '!' OR LOWER(system_name) LIKE ? ESCAPE '!') ORDER BY line_no LIMIT ? OFFSET ?",
                     source,
                     pattern,
                     pattern,
                     limit,
                     offset);
    }

    /** Searches all list sources without assigning trust or preferring one source. */
    public List<DirectoryEntry> search(String text, int offset, int limit) throws IOException {
        return search(text, offset, limit, true);
    }

    public List<DirectoryEntry> search(String text, int offset, int limit, boolean includePoints)
            throws IOException {
        if (text == null || text.length() > 160 || offset < 0 || offset > 1000000 || limit < 1
                || limit > 500) {
            throw new IllegalArgumentException("Search needs at most 160 characters, offset 0..1000000 and limit 1..500");
        }
        String value = text.trim();
        String pointFilter = includePoints ? "" : " AND address LIKE '%.0'";
        if (value.matches("\\d{1,5}:\\d{0,5}(?:/\\d{0,5}(?:\\.\\d{0,5})?)?")) {
            return query("SELECT "
                    + COLUMNS + " FROM directory_entries WHERE address LIKE ?" + pointFilter
                    + " ORDER BY address,source_id LIMIT ? OFFSET ?", value + "%", limit, offset);
        }
        String pattern = "%"
                         + value.toLowerCase(Locale.ROOT)
                                .replace('_', ' ')
                                .replace("!", "!!")
                                .replace("%", "!%")
                         + "%";
        List<String> fields = List.of("address",
                                      "sysop",
                                      "system_name",
                                      "location",
                                      "phone",
                                      "flags",
                                      "source_id",
                                      "keyword");
        List<String> predicates = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (String field : fields) {
            predicates.add("LOWER(REPLACE(" + field + ", '_', ' ')) LIKE ? ESCAPE '!'");
            parameters.add(pattern);
        }
        predicates.add("REPLACE(phone, '-', '.') LIKE ? ESCAPE '!'");
        parameters.add(pattern);
        parameters.add(limit);
        parameters.add(offset);
        return query("SELECT "
                     + COLUMNS
                     + " FROM directory_entries WHERE ("
                     + String.join(" OR ", predicates)
                     + ")"
                     + pointFilter
                     + " ORDER BY address,source_id LIMIT ? OFFSET ?", parameters.toArray());
    }

    private List<DirectoryEntry> query(String sql, Object... parameters) throws IOException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            List<DirectoryEntry> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new DirectoryEntry(rows.getString(1),
                                                  new FTNAddr(rows.getString(2)),
                                                  rows.getString(3),
                                                  rows.getString(4),
                                                  rows.getString(5),
                                                  rows.getString(6),
                                                  rows.getString(7),
                                                  rows.getString(8),
                                                  (Integer) rows.getObject(9),
                                                  (Integer) rows.getObject(10),
                                                  rows.getString(11).isEmpty()
                                                          ? List.of()
                                                          : Arrays.asList(rows.getString(11)
                                                                              .split(",", -1)),
                                                  rows.getLong(12)));
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IOException("Unable to query node directory", e);
        }
    }

    public synchronized void removeSource(String source) throws IOException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                delete(connection, "directory_entries", source);
                delete(connection, "directory_sources", source);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Unable to remove directory source " + source, e);
        }
    }

    private static void delete(Connection connection, String table, String source)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM "
                                                                       + table
                                                                       + " WHERE source_id=?")) {
            statement.setString(1, source);
            statement.executeUpdate();
        }
    }

    private static String fingerprint(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = new DigestInputStream(limited(path), digest)) {
            drain(input);
        }
        return hex(digest.digest());
    }

    private static InputStream limited(Path path) throws IOException {
        return DirectoryInput.bounded(Files.newInputStream(path));
    }

    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[32768];
        while (input.read(buffer) != -1) {
            // Includes the original header, DOS EOF marker and ZIP central directory in SHA-256.
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 255));
        }
        return result.toString();
    }

    @Override
    public void close() {
        closed = true;
    }
}
