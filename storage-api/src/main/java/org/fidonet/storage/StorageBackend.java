package org.fidonet.storage;

import java.io.IOException;

/** Backend infrastructure is available to repository adapters, never to business plugins. */
public interface StorageBackend extends AutoCloseable {
    String dialect();

    int schemaVersion(String module) throws IOException;

    void recordSchemaVersion(String module, int version) throws IOException;

    @Override
    void close() throws IOException;
}
