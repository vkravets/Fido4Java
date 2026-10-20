package org.fidonet.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/** Process-local owner of backend leases, including setup and offline operations. */
public final class StorageServices {
    private static final StorageServices DEFAULT = new StorageServices();
    private final Map<String, Shared> backends = new HashMap<>();
    private final List<StorageProvider> providers;
    private final List<RepositoryAdapter<?>> adapters;

    public StorageServices() {
        providers = new ArrayList<>();
        ServiceLoader.load(StorageProvider.class).forEach(providers::add);
        adapters = new ArrayList<>();
        ServiceLoader.load(RepositoryAdapter.class).forEach(adapters::add);
    }

    public StorageServices(List<StorageProvider> providers, List<RepositoryAdapter<?>> adapters) {
        this.providers = List.copyOf(providers);
        this.adapters = List.copyOf(adapters);
    }

    /** Existing file locations are preserved; no implicit cross-database data migration occurs. */
    public static StorageSession file(Path path) throws IOException {
        return DEFAULT.open("h2", path.toAbsolutePath().normalize().toString());
    }

    public synchronized StorageSession open(String provider, String location) throws IOException {
        String key = provider + "\u0000" + location;
        Shared shared = backends.get(key);
        if (shared == null) {
            List<StorageProvider> matches = new ArrayList<>();
            for (StorageProvider candidate : providers) {
                if (candidate.id().equals(provider)) {
                    matches.add(candidate);
                }
            }
            if (matches.size() != 1) {
                throw new IOException("Expected one storage provider for "
                                      + provider
                                      + ", found "
                                      + matches.size());
            }
            shared = new Shared(matches.get(0).open(location));
            backends.put(key, shared);
        }
        shared.references++;
        return new Lease(key, shared);
    }

    private final class Lease implements StorageSession {
        private final String key;
        private final Shared shared;
        private boolean closed;

        private Lease(String key, Shared shared) {
            this.key = key;
            this.shared = shared;
        }

        @Override
        public <T extends AutoCloseable> T require(Class<T> contract) throws IOException {
            synchronized (StorageServices.this) {
                if (closed) {
                    throw new IOException("Storage lease is closed");
                }
                return repository(shared, contract);
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (StorageServices.this) {
                if (closed) {
                    return;
                }
                closed = true;
                if (--shared.references == 0) {
                    try {
                        shared.close();
                    } finally {
                        backends.remove(key);
                    }
                }
            }
        }
    }

    private <T extends AutoCloseable> T repository(Shared shared, Class<T> contract)
            throws IOException {
        AutoCloseable existing = shared.repositories.get(contract);
        if (existing != null) {
            return contract.cast(existing);
        }
        List<RepositoryAdapter<?>> matches = new ArrayList<>();
        for (RepositoryAdapter<?> adapter : adapters) {
            if (adapter.contract().equals(contract) && adapter.supports(shared.backend.dialect())) {
                matches.add(adapter);
            }
        }
        if (matches.size() != 1) {
            throw new IOException("Expected one adapter for "
                                  + contract.getName()
                                  + " on "
                                  + shared.backend.dialect()
                                  + ", found "
                                  + matches.size());
        }
        T repository = contract.cast(matches.get(0).open(shared.backend));
        shared.repositories.put(contract, repository);
        return repository;
    }

    private static final class Shared implements AutoCloseable {
        private final StorageBackend backend;
        private final Map<Class<?>, AutoCloseable> repositories = new LinkedHashMap<>();
        private int references;

        private Shared(StorageBackend backend) {
            this.backend = backend;
        }

        @Override
        public void close() throws IOException {
            List<AutoCloseable> resources = new ArrayList<>(repositories.values());
            Collections.reverse(resources);
            resources.add(backend);
            IOException failure = null;
            for (AutoCloseable resource : resources) {
                try {
                    resource.close();
                } catch (Exception error) {
                    if (failure == null) {
                        failure = new IOException("Unable to close storage", error);
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
