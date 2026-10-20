package org.fidonet.storage;

import java.io.IOException;

/** Backend infrastructure is available to repository adapters, never to business plugins. */
public interface StorageBackend extends AutoCloseable {
    String dialect();

    @Override
    void close() throws IOException;
}
