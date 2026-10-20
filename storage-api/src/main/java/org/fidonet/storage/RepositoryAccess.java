package org.fidonet.storage;

import java.io.IOException;

/** Typed extension point exposed by storage facades; no backend or SQL access. */
public interface RepositoryAccess {
    <T extends AutoCloseable> T repository(Class<T> contract) throws IOException;
}
