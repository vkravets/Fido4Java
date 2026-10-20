package org.fidonet.storage.jdbc;

import org.fidonet.storage.StorageBackend;

import javax.sql.DataSource;

/** Infrastructure contract for JDBC adapters; not exposed by domain repositories. */
public interface JdbcBackend extends StorageBackend {
    DataSource dataSource();
}
