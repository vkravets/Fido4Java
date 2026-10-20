package org.fidonet.storage;

import java.io.IOException;

/** A lease on shared repositories. Clients close the lease, not individual repositories. */
public interface StorageSession extends AutoCloseable {
    <T extends AutoCloseable> T require(Class<T> contract) throws IOException;

    @Override
    void close() throws IOException;
}
