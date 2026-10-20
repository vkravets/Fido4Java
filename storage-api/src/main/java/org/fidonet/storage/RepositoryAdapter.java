package org.fidonet.storage;

import java.io.IOException;

/** A backend-specific implementation of a high-level repository contract. */
public interface RepositoryAdapter<T extends AutoCloseable> {
    Class<T> contract();

    boolean supports(String dialect);

    /** Persistent module schema version, independent of the application/plugin version. */
    default int schemaVersion() {
        return 1;
    }

    T open(StorageBackend backend) throws IOException;
}
