package org.fidonet.storage;

import java.io.IOException;

/** Supplies a backend for an opaque provider-specific location. */
public interface StorageProvider {
    String id();

    default String normalizeLocation(String location) throws IOException {
        return location;
    }

    /** Remote providers may validate availability when opening the repository. */
    default boolean exists(String location) throws IOException {
        return true;
    }

    StorageBackend open(String location) throws IOException;
}
