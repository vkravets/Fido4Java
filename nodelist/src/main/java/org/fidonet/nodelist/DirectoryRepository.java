package org.fidonet.nodelist;

import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Backend-independent transactional imports and bounded directory queries. */
public interface DirectoryRepository extends AutoCloseable {
    DirectoryImportResult importSource(DirectorySource source) throws IOException;

    void validateSource(DirectorySource source) throws IOException;

    Optional<DirectoryEntry> lookup(String source, FTNAddr address) throws IOException;

    List<DirectoryEntry> lookup(FTNAddr address) throws IOException;

    List<DirectoryEntry> search(String source, String name, int offset, int limit)
            throws IOException;

    List<DirectoryEntry> search(String text, int offset, int limit) throws IOException;

    List<DirectoryEntry> search(String text, int offset, int limit, boolean includePoints)
            throws IOException;

    void removeSource(String source) throws IOException;

    @Override
    void close() throws IOException;
}
