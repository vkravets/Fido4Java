package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.MailDraft;

import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** UI-owned draft/read/submission state, separate from the mail database. */
final class UiState {
    private final String url;

    UiState(Path path) throws Exception {
        if (path.toString().contains(";")) {
            throw new IllegalArgumentException("Invalid UI state path");
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        url = "jdbc:h2:" + path.toAbsolutePath();
        try (Connection connection = open(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS drafts (owner VARCHAR(100), id VARCHAR(80), payload BLOB NOT NULL, PRIMARY KEY(owner,id))");
            sql.execute("CREATE TABLE IF NOT EXISTS read_marks (owner VARCHAR(100), mailbox VARCHAR(10), area VARCHAR(255), id BIGINT, PRIMARY KEY(owner,mailbox,area,id))");
            sql.execute("CREATE TABLE IF NOT EXISTS submissions (id VARCHAR(64) PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL, message_id VARCHAR(255) NOT NULL)");
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection(url);
    }

    boolean isRead(String owner, String box, String area, long id) throws Exception {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT 1 FROM read_marks WHERE owner=? AND mailbox=? AND area=? AND id=?")) {
            sql.setString(1, owner);
            sql.setString(2, box);
            sql.setString(3, area);
            sql.setLong(4, id);
            try (ResultSet result = sql.executeQuery()) {
                return result.next();
            }
        }
    }

    Set<Long> readIds(String owner, String box, String area, List<Long> ids) throws Exception {
        Set<Long> result = new HashSet<>();
        if (ids.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT id FROM read_marks WHERE owner=? AND mailbox=? AND area=? AND id IN ("
                        + placeholders + ")")) {
            sql.setString(1, owner);
            sql.setString(2, box);
            sql.setString(3, area);
            for (int i = 0; i < ids.size(); i++) {
                sql.setLong(i + 4, ids.get(i));
            }
            try (ResultSet rows = sql.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getLong(1));
                }
            }
        }
        return result;
    }

    void mark(String owner, String box, String area, long id, boolean read) throws Exception {
        String query = read
                ? "MERGE INTO read_marks(owner,mailbox,area,id) KEY(owner,mailbox,area,id) VALUES(?,?,?,?)"
                : "DELETE FROM read_marks WHERE owner=? AND mailbox=? AND area=? AND id=?";
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement(query)) {
            sql.setString(1, owner);
            sql.setString(2, box);
            sql.setString(3, area);
            sql.setLong(4, id);
            sql.executeUpdate();
        }
    }

    List<MailDraft> drafts(String owner) throws Exception {
        List<MailDraft> drafts = new ArrayList<>();
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT payload FROM drafts WHERE owner=? ORDER BY id LIMIT 200")) {
            sql.setString(1, owner);
            try (ResultSet result = sql.executeQuery()) {
                while (result.next()) {
                    MailDraft draft = new MailDraft();
                    new TDeserializer().deserialize(draft, result.getBytes(1));
                    drafts.add(draft);
                }
            }
        }
        return drafts;
    }

    synchronized void save(String owner, MailDraft draft) throws Exception {
        try (Connection connection = open()) {
            if (countOtherDrafts(connection, owner, draft.getId()) >= 200) {
                throw new IllegalArgumentException("At most 200 drafts are supported");
            }
            saveDraft(connection, owner, draft);
        }
    }

    private long countOtherDrafts(Connection connection, String owner, String id) throws Exception {
        try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM drafts WHERE owner=? AND id<>?")) {
            count.setString(1, owner);
            count.setString(2, id);
            try (ResultSet result = count.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void saveDraft(Connection connection, String owner, MailDraft draft) throws Exception {
        try (PreparedStatement sql = connection.prepareStatement("MERGE INTO drafts(owner,id,payload) KEY(owner,id) VALUES(?,?,?)")) {
            sql.setString(1, owner);
            sql.setString(2, draft.getId());
            sql.setBytes(3, new TSerializer().serialize(draft));
            sql.executeUpdate();
        }
    }

    void delete(String owner, String id) throws Exception {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("DELETE FROM drafts WHERE owner=? AND id=?")) {
            sql.setString(1, owner);
            sql.setString(2, id);
            sql.executeUpdate();
        }
    }

    void reserve(String id, String fingerprint, String messageId) throws Exception {
        try (Connection connection = open()) {
            String existing = submissionFingerprint(connection, id);
            if (existing != null) {
                requireSameFingerprint(existing, fingerprint);
                return;
            }
            insertSubmission(connection, id, fingerprint, messageId);
        }
    }

    private String submissionFingerprint(Connection connection, String id) throws Exception {
        try (PreparedStatement sql = connection.prepareStatement("SELECT fingerprint FROM submissions WHERE id=?")) {
            sql.setString(1, id);
            try (ResultSet result = sql.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void requireSameFingerprint(String existing, String fingerprint) {
        if (!fingerprint.equals(existing)) {
            throw new IllegalArgumentException("Request ID was used with different content");
        }
    }

    private void insertSubmission(Connection connection,
                                  String id,
                                  String fingerprint,
                                  String messageId)
            throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO submissions(id,fingerprint,message_id) VALUES(?,?,?)")) {
            insert.setString(1, id);
            insert.setString(2, fingerprint);
            insert.setString(3, messageId);
            insert.executeUpdate();
        }
    }
}
