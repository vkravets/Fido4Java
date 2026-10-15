package org.fidonet.db;

import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.stmt.QueryBuilder;

import java.sql.SQLException;
import java.util.Iterator;

/** Adapts an ORM cursor to structured resource ownership. */
final class QueryCursor<T> implements Iterator<T>, AutoCloseable {
    private final CloseableIterator<T> rows;

    QueryCursor(QueryBuilder<T, Object> query) throws SQLException {
        rows = query.iterator();
    }

    @Override
    public boolean hasNext() {
        return rows.hasNext();
    }

    @Override
    public T next() {
        return rows.next();
    }

    @Override
    public void close() throws SQLException {
        closeRows(rows);
    }

    static void closeRows(CloseableIterator<?> rows) throws SQLException {
        try {
            rows.close();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Unable to close database iterator", e);
        }
    }
}
