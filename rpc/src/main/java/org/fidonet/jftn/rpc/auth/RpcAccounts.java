package org.fidonet.jftn.rpc.auth;

import org.fidonet.jftn.rpc.PasskeyInfo;

import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.ByteArray;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Persistent RPC identities and public credentials, independent of FTN link passwords. */
public final class RpcAccounts implements AutoCloseable {
    private static final int ITERATIONS = 600000;
    private static final SecureRandom random = new SecureRandom();
    private final String url;
    private final Connection keeper;
    private volatile boolean closed;

    public RpcAccounts(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.toString().contains(";")) {
            throw new IllegalArgumentException("Invalid authentication database path");
        }
        Files.createDirectories(absolute.getParent());
        url = "jdbc:h2:" + absolute;
        try {
            keeper = open();
            try (Statement sql = keeper.createStatement()) {
                sql.execute("CREATE TABLE IF NOT EXISTS rpc_users (login VARCHAR(100) PRIMARY KEY, password_hash VARCHAR(256) NOT NULL, user_handle VARBINARY(64) NOT NULL UNIQUE, enabled BOOLEAN NOT NULL)");
                sql.execute("CREATE TABLE IF NOT EXISTS rpc_passkeys (credential_id VARCHAR(2048) NOT NULL, rp_id VARCHAR(255) NOT NULL, owner VARCHAR(100) NOT NULL REFERENCES rpc_users(login), public_key BLOB NOT NULL, sign_count BIGINT NOT NULL, backup_eligible BOOLEAN NOT NULL, backup_state BOOLEAN NOT NULL, label VARCHAR(100) NOT NULL, created BIGINT NOT NULL, PRIMARY KEY(rp_id,credential_id))");
                sql.execute("ALTER TABLE rpc_users ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'ADMIN'");
                sql.execute("ALTER TABLE rpc_users ADD COLUMN IF NOT EXISTS theme VARCHAR(8) NOT NULL DEFAULT 'light'");
                sql.execute("ALTER TABLE rpc_users ADD COLUMN IF NOT EXISTS default_aka VARCHAR(40) NOT NULL DEFAULT ''");
                sql.execute("CREATE TABLE IF NOT EXISTS rpc_akas (owner VARCHAR(100) NOT NULL REFERENCES rpc_users(login), address VARCHAR(40) NOT NULL, state VARCHAR(16) NOT NULL, PRIMARY KEY(owner,address))");
                sql.execute("CREATE TABLE IF NOT EXISTS rpc_point_addresses (address VARCHAR(40) PRIMARY KEY, owner VARCHAR(100) NOT NULL REFERENCES rpc_users(login))");
            } catch (SQLException failure) {
                keeper.close();
                throw failure;
            }
        } catch (SQLException e) {
            throw new IOException("Cannot open RPC account database", e);
        }
    }

    Connection open() throws SQLException {
        if (closed) {
            throw new SQLException("Authentication database is closed");
        }
        return DriverManager.getConnection(url);
    }

    public synchronized void bootstrap(String user, String password) throws IOException {
        try (Connection connection = open();
                Statement sql = connection.createStatement();
                ResultSet result = sql.executeQuery("SELECT COUNT(*) FROM rpc_users")) {
            result.next();
            if (result.getLong(1) == 0) {
                createUser(user, password);
            }
        } catch (SQLException e) {
            throw new IOException("Cannot bootstrap RPC account", e);
        }
    }

    public synchronized void createUser(String user, String password) throws IOException {
        try (Connection connection = open()) {
            insertUser(connection, user, password, "ADMIN", true);
        } catch (SQLException e) {
            throw new IOException("Cannot create RPC account", e);
        }
    }

    void insertUser(Connection connection,
                    String user,
                    String password,
                    String role,
                    boolean enabled)
            throws IOException, SQLException {
        if (user == null || !user.matches("[A-Za-z0-9_.@-]{1,100}") || password == null
                || password.isEmpty() || password.length() > 1024) {
            throw new IllegalArgumentException("Initial RPC account needs a valid login and password");
        }
        try (PreparedStatement sql = connection.prepareStatement("INSERT INTO rpc_users(login,password_hash,user_handle,role,enabled) VALUES(?,?,?,?,?)")) {
            sql.setString(1, user);
            sql.setString(2, passwordHash(password));
            sql.setBytes(3, randomBytes(32));
            sql.setString(4, role);
            sql.setBoolean(5, enabled);
            sql.executeUpdate();
        }
    }

    public boolean hasUsers() throws IOException {
        try (Connection connection = open();
                Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM rpc_users")) {
            rows.next();
            return rows.getLong(1) != 0;
        } catch (SQLException failure) {
            throw new IOException("Cannot inspect RPC accounts", failure);
        }
    }

    public boolean enabled(String user) throws IOException {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT enabled FROM rpc_users WHERE login=?")) {
            sql.setString(1, user);
            try (ResultSet result = sql.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IOException("Cannot read RPC account", e);
        }
    }

    public boolean verifyPassword(String user, String password) throws IOException {
        String hash = null;
        boolean enabled = false;
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT password_hash,enabled FROM rpc_users WHERE login=?")) {
            sql.setString(1, user);
            try (ResultSet result = sql.executeQuery()) {
                if (result.next()) {
                    hash = result.getString(1);
                    enabled = result.getBoolean(2);
                }
            }
        } catch (SQLException e) {
            throw new IOException("Cannot authenticate RPC account", e);
        }
        if (password == null || password.length() > 1024) {
            return false;
        }
        if (hash == null) {
            derive(password, new byte[16], ITERATIONS);
            return false;
        }
        String[] parts = hash.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) {
            throw new IOException("Unsupported password hash");
        }
        int iterations = Integer.parseInt(parts[1]);
        if (iterations < 100000 || iterations > 2000000) {
            throw new IOException("Invalid password hash parameters");
        }
        return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                                     derive(password,
                                            Base64.getDecoder().decode(parts[2]),
                                            iterations))
                && enabled;
    }

    public synchronized void changePassword(String user, String current, String replacement)
            throws IOException {
        if (replacement == null || replacement.length() < 12 || replacement.length() > 1024) {
            throw new IllegalArgumentException("New password must contain 12..1024 characters");
        }
        if (!verifyPassword(user, current)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("UPDATE rpc_users SET password_hash=? WHERE login=? AND enabled=TRUE")) {
            sql.setString(1, passwordHash(replacement));
            sql.setString(2, user);
            if (sql.executeUpdate() != 1) {
                throw new IOException("RPC account was disabled");
            }
        } catch (SQLException e) {
            throw new IOException("Cannot change RPC password", e);
        }
    }

    private static String passwordHash(String password) throws IOException {
        byte[] salt = randomBytes(16);
        return "pbkdf2-sha256$"
               + ITERATIONS
               + "$"
               + Base64.getEncoder()
                       .encodeToString(salt)
               + "$"
               + Base64.getEncoder()
                       .encodeToString(derive(password, salt, ITERATIONS));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws IOException {
        char[] characters = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                   .generateSecret(spec)
                                   .getEncoded();
        } catch (Exception e) {
            throw new IOException("Password hashing is unavailable", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }

    static byte[] randomBytes(int size) {
        byte[] value = new byte[size];
        random.nextBytes(value);
        return value;
    }

    public Optional<ByteArray> handle(String user) {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT user_handle FROM rpc_users WHERE login=? AND enabled=TRUE")) {
            sql.setString(1, user);
            try (ResultSet result = sql.executeQuery()) {
                return result.next()
                        ? Optional.of(new ByteArray(result.getBytes(1)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read user handle", e);
        }
    }

    public Optional<String> username(ByteArray handle) {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT login FROM rpc_users WHERE user_handle=? AND enabled=TRUE")) {
            sql.setBytes(1, handle.getBytes());
            try (ResultSet result = sql.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read credential owner", e);
        }
    }

    List<RegisteredCredential> credentials(String rp, String owner, String credentialId) {
        String query = "SELECT p.*,u.user_handle FROM rpc_passkeys p JOIN rpc_users u ON p.owner=u.login WHERE p.rp_id=? AND u.enabled=TRUE";
        if (owner != null) {
            query += " AND p.owner=?";
        }
        if (credentialId != null) {
            query += " AND p.credential_id=?";
        }
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement(query)) {
            sql.setString(1, rp);
            int parameter = 2;
            if (owner != null) {
                sql.setString(parameter++, owner);
            }
            if (credentialId != null) {
                sql.setString(parameter, credentialId);
            }
            List<RegisteredCredential> result = new ArrayList<>();
            try (ResultSet rows = sql.executeQuery()) {
                while (rows.next()) {
                    result.add(RegisteredCredential.builder()
                                                   .credentialId(ByteArray.fromBase64Url(rows.getString("credential_id")))
                                                   .userHandle(new ByteArray(rows.getBytes("user_handle")))
                                                   .publicKeyCose(new ByteArray(rows.getBytes("public_key")))
                                                   .signatureCount(rows.getLong("sign_count"))
                                                   .backupEligible(rows.getBoolean("backup_eligible"))
                                                   .backupState(rows.getBoolean("backup_state"))
                                                   .build());
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read stored passkeys", e);
        }
    }

    synchronized void register(String owner, String rp, String label, RegistrationResult credential)
            throws IOException {
        if (list(owner, rp).size() >= 10) {
            throw new IllegalArgumentException("At most 10 passkeys per account are supported");
        }
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("INSERT INTO rpc_passkeys(credential_id,rp_id,owner,public_key,sign_count,backup_eligible,backup_state,label,created) VALUES(?,?,?,?,?,?,?,?,?)")) {
            sql.setString(1, credential.getKeyId().getId().getBase64Url());
            sql.setString(2, rp);
            sql.setString(3, owner);
            sql.setBytes(4, credential.getPublicKeyCose().getBytes());
            sql.setLong(5, credential.getSignatureCount());
            sql.setBoolean(6, credential.isBackupEligible());
            sql.setBoolean(7, credential.isBackedUp());
            sql.setString(8, label);
            sql.setLong(9, System.currentTimeMillis());
            sql.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Cannot register passkey", e);
        }
    }

    synchronized boolean updateCounter(String rp,
                                       String owner,
                                       ByteArray id,
                                       long previous,
                                       long current,
                                       boolean backedUp)
            throws IOException {
        if (current < previous || current == previous && current != 0) {
            return false;
        }
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("UPDATE rpc_passkeys SET sign_count=?,backup_state=? WHERE rp_id=? AND owner=? AND credential_id=? AND sign_count=?")) {
            sql.setLong(1, current);
            sql.setBoolean(2, backedUp);
            sql.setString(3, rp);
            sql.setString(4, owner);
            sql.setString(5, id.getBase64Url());
            sql.setLong(6, previous);
            return sql.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IOException("Cannot update passkey counter", e);
        }
    }

    public List<PasskeyInfo> list(String owner, String rp) throws IOException {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("SELECT credential_id,label,created FROM rpc_passkeys WHERE owner=? AND rp_id=? ORDER BY created,credential_id")) {
            sql.setString(1, owner);
            sql.setString(2, rp);
            List<PasskeyInfo> result = new ArrayList<>();
            try (ResultSet rows = sql.executeQuery()) {
                while (rows.next()) {
                    result.add(new PasskeyInfo(rows.getString(1),
                                               rows.getString(2),
                                               rows.getLong(3)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IOException("Cannot list passkeys", e);
        }
    }

    public synchronized void remove(String owner, String rp, String id) throws IOException {
        try (Connection connection = open();
                PreparedStatement sql = connection.prepareStatement("DELETE FROM rpc_passkeys WHERE owner=? AND rp_id=? AND credential_id=?")) {
            sql.setString(1, owner);
            sql.setString(2, rp);
            sql.setString(3, id);
            sql.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Cannot remove passkey", e);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        try {
            keeper.close();
        } catch (SQLException e) {
            throw new IOException("Cannot close authentication database", e);
        }
    }
}
