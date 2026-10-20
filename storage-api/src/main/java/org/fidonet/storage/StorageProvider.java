package org.fidonet.storage;

import java.io.IOException;

/** Supplies a backend for an opaque provider-specific location. */
public interface StorageProvider {
    String id();

    StorageBackend open(String location) throws IOException;
}
