package org.fidonet.db;

import org.fidonet.storage.jdbc.JdbcBackend;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

/** Scoped bridge for standalone ORM tools; running nodes use the provider-owned backend. */
final class OrmExtensionBackend implements JdbcBackend {
    private final OrmManager orm;
    private final Map<String, Integer> schemas = new HashMap<>();

    OrmExtensionBackend(OrmManager orm) {
        this.orm = orm;
    }

    @Override
    public String dialect() {
        return orm.connectionSource.getDatabaseType().getDatabaseName();
    }

    @Override
    public DataSource dataSource() {
        return orm.extensionDataSource();
    }

    @Override
    public int schemaVersion(String module) {
        return schemas.getOrDefault(module, 0);
    }

    @Override
    public void recordSchemaVersion(String module, int version) {
        schemas.put(module, version);
    }

    @Override
    public void close() {
        // The enclosing ORM facade owns the connection source.
    }
}
