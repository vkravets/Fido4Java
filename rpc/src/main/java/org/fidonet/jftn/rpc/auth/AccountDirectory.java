package org.fidonet.jftn.rpc.auth;

import org.fidonet.types.FTNAddr;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Transactional account registration, preferences and approved posting identities. */
public final class AccountDirectory {
    private final RpcAccounts accounts;

    public AccountDirectory(RpcAccounts accounts) {
        this.accounts = accounts;
    }

    public AccountRecord profile(String login) throws SQLException {
        try (Connection connection = accounts.open();
                PreparedStatement sql = connection.prepareStatement("SELECT login,role,enabled,theme,default_aka FROM rpc_users WHERE login=?")) {
            sql.setString(1, login);
            try (ResultSet rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Unknown account");
                }
                return record(rows);
            }
        }
    }

    public List<AccountRecord> users() throws SQLException {
        try (Connection connection = accounts.open();
                PreparedStatement sql = connection.prepareStatement("SELECT login,role,enabled,theme,default_aka FROM rpc_users ORDER BY enabled,login LIMIT 1024");
                ResultSet rows = sql.executeQuery()) {
            List<AccountRecord> result = new ArrayList<>();
            while (rows.next()) {
                result.add(record(rows));
            }
            return result;
        }
    }

    private static AccountRecord record(ResultSet rows) throws SQLException {
        return new AccountRecord(rows.getString(1),
                                 rows.getString(2),
                                 rows.getBoolean(3),
                                 rows.getString(4),
                                 rows.getString(5));
    }

    public synchronized void register(String login, String password, String requestedAka)
            throws Exception {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters");
        }
        String address = address(requestedAka, true);
        transaction(connection -> {
            requireRegistrationCapacity(connection);
            accounts.insertUser(connection, login, password, "POINT", false);
            insertClaim(connection, login, address, "PENDING");
        });
    }

    private static void requireRegistrationCapacity(Connection connection) throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("SELECT COUNT(*) FROM rpc_users WHERE enabled=FALSE");
                ResultSet rows = sql.executeQuery()) {
            rows.next();
            if (rows.getLong(1) >= 64) {
                throw new IllegalArgumentException("Registration queue is full");
            }
        }
    }

    public List<AddressClaim> claims(String owner) throws SQLException {
        try (Connection connection = accounts.open();
                PreparedStatement sql = connection.prepareStatement("SELECT address,state FROM rpc_akas WHERE owner=? ORDER BY address")) {
            sql.setString(1, owner);
            try (ResultSet rows = sql.executeQuery()) {
                List<AddressClaim> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new AddressClaim(rows.getString(1), rows.getString(2)));
                }
                return result;
            }
        }
    }

    public synchronized void requestAka(String owner, String value) throws Exception {
        AccountRecord user = profile(owner);
        String address = address(value, !user.role.equals("ADMIN"));
        if (claims(owner).size() >= 16) {
            throw new IllegalArgumentException("At most 16 address claims are supported");
        }
        transaction(connection -> insertClaim(connection,
                                              owner,
                                              address,
                                              user.role.equals("ADMIN") ? "APPROVED" : "PENDING"));
    }

    public synchronized void decide(String owner, String value, boolean approve) throws Exception {
        String address = address(value, true);
        transaction(connection -> {
            if (approve) {
                reservePointAddress(connection, owner, address);
            }
            updateClaim(connection, owner, address, approve ? "APPROVED" : "REJECTED");
            if (approve) {
                enablePoint(connection, owner, address);
            }
        });
    }

    private static void reservePointAddress(Connection connection, String owner, String address)
            throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("SELECT owner FROM rpc_point_addresses WHERE address=?")) {
            sql.setString(1, address);
            try (ResultSet rows = sql.executeQuery()) {
                if (rows.next()) {
                    requireOwner(rows.getString(1), owner);
                    return;
                }
            }
        }
        try (PreparedStatement sql = connection.prepareStatement("INSERT INTO rpc_point_addresses(address,owner) VALUES(?,?)")) {
            sql.setString(1, address);
            sql.setString(2, owner);
            sql.executeUpdate();
        }
    }

    private static void requireOwner(String existing, String owner) {
        if (!existing.equals(owner)) {
            throw new IllegalArgumentException("Address already belongs to another point account");
        }
    }

    private static void enablePoint(Connection connection, String owner, String address)
            throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("UPDATE rpc_users SET enabled=TRUE,default_aka=CASE WHEN default_aka='' THEN ? ELSE default_aka END WHERE login=? AND role='POINT'")) {
            sql.setString(1, address);
            sql.setString(2, owner);
            sql.executeUpdate();
        }
    }

    private static void updateClaim(Connection connection,
                                    String owner,
                                    String address,
                                    String state)
            throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("UPDATE rpc_akas SET state=? WHERE owner=? AND address=? AND state='PENDING'")) {
            sql.setString(1, state);
            sql.setString(2, owner);
            sql.setString(3, address);
            if (sql.executeUpdate() != 1) {
                throw new IllegalArgumentException("Pending address request was not found");
            }
        }
    }

    public synchronized void preferences(String owner,
                                         String theme,
                                         String defaultAka,
                                         List<String> stationAddresses)
            throws Exception {
        if (!theme.equals("light") && !theme.equals("dark")) {
            throw new IllegalArgumentException("Unknown theme");
        }
        String address = defaultAka.isEmpty() ? "" : address(defaultAka, false);
        if (!address.isEmpty() && !allowed(owner, stationAddresses).contains(address)) {
            throw new IllegalArgumentException("Posting address is not approved for this account");
        }
        try (Connection connection = accounts.open();
                PreparedStatement sql = connection.prepareStatement("UPDATE rpc_users SET theme=?,default_aka=? WHERE login=?")) {
            sql.setString(1, theme);
            sql.setString(2, address);
            sql.setString(3, owner);
            sql.executeUpdate();
        }
    }

    public List<String> allowed(String owner, List<String> stationAddresses) throws SQLException {
        List<String> result = new ArrayList<>();
        if (profile(owner).role.equals("ADMIN")) {
            for (String value : stationAddresses) {
                result.add(address(value, false));
            }
        }
        for (AddressClaim claim : claims(owner)) {
            if (claim.state.equals("APPROVED") && !result.contains(claim.address)) {
                result.add(claim.address);
            }
        }
        return result;
    }

    private static void insertClaim(Connection connection,
                                    String owner,
                                    String address,
                                    String state)
            throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("INSERT INTO rpc_akas(owner,address,state) VALUES(?,?,?)")) {
            sql.setString(1, owner);
            sql.setString(2, address);
            sql.setString(3, state);
            sql.executeUpdate();
        }
    }

    public static String address(String value, boolean pointOnly) {
        if (value == null || !value.matches("[0-9]+:[0-9]+/[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Expected a numeric FTN address");
        }
        FTNAddr parsed = new FTNAddr(value);
        for (int part : new int[]{parsed.getZone(), parsed.getNet(), parsed.getNode(),
                parsed.getPnt()}) {
            if (part < 0 || part > 65535) {
                throw new IllegalArgumentException("FTN address component is outside 0..65535");
            }
        }
        if (parsed.getZone() == 0 || pointOnly && parsed.getPnt() == 0) {
            throw new IllegalArgumentException("Registration requires a point address with an explicit zone");
        }
        return parsed.toString();
    }

    @FunctionalInterface
    private interface Change {
        void apply(Connection connection) throws Exception;
    }

    private void transaction(Change change) throws Exception {
        try (Connection connection = accounts.open()) {
            connection.setAutoCommit(false);
            applyTransaction(connection, change);
        }
    }

    private static void applyTransaction(Connection connection, Change change) throws Exception {
        try {
            change.apply(connection);
            connection.commit();
        } catch (Exception failure) {
            rollback(connection, failure);
            throw failure;
        }
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }
}
