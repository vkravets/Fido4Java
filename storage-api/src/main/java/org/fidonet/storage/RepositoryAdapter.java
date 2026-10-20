package org.fidonet.storage;

import java.io.IOException;

/** A backend-specific implementation of a high-level repository contract. */
public interface RepositoryAdapter<T extends AutoCloseable> {
    Class<T> contract();

    boolean supports(String dialect);

    T open(StorageBackend backend) throws IOException;
}
