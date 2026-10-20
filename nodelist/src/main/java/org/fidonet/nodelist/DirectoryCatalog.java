package org.fidonet.nodelist;

import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A domain facade; SQL and connections belong to the selected storage adapter. */
public final class DirectoryCatalog implements AutoCloseable {
    private final StorageSession storage;
    private final DirectoryRepository repository;
    private boolean closed;

    public DirectoryCatalog(Path path) throws IOException {
        this(StorageServices.file(path));
    }

    public DirectoryCatalog(StorageSession storage) throws IOException {
        this.storage = storage;
        try {
            repository = storage.require(DirectoryRepository.class);
        } catch (IOException | RuntimeException failure) {
            try {
                storage.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private DirectoryRepository repository() throws IOException {
        if (closed) {
            throw new IOException("Directory catalog has been closed");
        }
        return repository;
    }

    public DirectoryImportResult importSource(DirectorySource source) throws IOException {
        return repository().importSource(source);
    }

    public void validateSource(DirectorySource source) throws IOException {
        repository().validateSource(source);
    }

    public Optional<DirectoryEntry> lookup(String source, FTNAddr address) throws IOException {
        return repository().lookup(source, address);
    }

    public List<DirectoryEntry> lookup(FTNAddr address) throws IOException {
        return repository().lookup(address);
    }

    public List<DirectoryEntry> search(String source, String name, int offset, int limit)
            throws IOException {
        return repository().search(source, name, offset, limit);
    }

    public List<DirectoryEntry> search(String text, int offset, int limit) throws IOException {
        return repository().search(text, offset, limit);
    }

    public List<DirectoryEntry> search(String text, int offset, int limit, boolean includePoints)
            throws IOException {
        return repository().search(text, offset, limit, includePoints);
    }

    public void removeSource(String source) throws IOException {
        repository().removeSource(source);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        storage.close();
    }
}
