package org.fidonet.db;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Standalone ORM clients share their existing location with typed extension adapters. */
final class OrmDataSource implements DataSource {
    private final String url;
    private final String user;
    private final String password;

    OrmDataSource(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return user == null
                ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, user, password);
    }

    @Override
    public Connection getConnection(String username, String secret) throws SQLException {
        throw new SQLFeatureNotSupportedException("Per-operation credentials are not supported");
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter writer) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException("Unsupported data source wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }
}
