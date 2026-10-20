package org.fidonet.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Process-local owner of backend leases, including setup and offline operations. */
public final class StorageServices {
    private static final StorageServices DEFAULT = new StorageServices();
    private final Map<String, Shared> backends = new HashMap<>();
    private final List<StorageProvider> providers;
    private final List<RepositoryAdapter<?>> adapters;
    private final List<ServiceConfigurationError> unavailableAdapters = new ArrayList<>();

    public StorageServices() {
        providers = new ArrayList<>();
        ServiceLoader.load(StorageProvider.class).forEach(providers::add);
        adapters = new ArrayList<>();
        Iterator<RepositoryAdapter> discovered = ServiceLoader.load(RepositoryAdapter.class)
                                                              .iterator();
        while (discovered.hasNext()) {
            try {
                adapters.add(discovered.next());
            } catch (ServiceConfigurationError failure) {
                if (!missingDependency(failure)) {
                    throw failure;
                }
                unavailableAdapters.add(failure);
            }
        }
    }

    public StorageServices(List<StorageProvider> providers, List<RepositoryAdapter<?>> adapters) {
        this.providers = List.copyOf(providers);
        this.adapters = List.copyOf(adapters);
    }

    private static boolean missingDependency(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof NoClassDefFoundError || cause instanceof ClassNotFoundException) {
                return true;
            }
        }
        return false;
    }

    /** Existing file locations are preserved; no implicit cross-database data migration occurs. */
    public static StorageSession file(Path path) throws IOException {
        return DEFAULT.open("h2", path.toAbsolutePath().normalize().toString());
    }

    /** Opens a configured provider without exposing infrastructure to the calling plugin. */
    public static StorageSession shared(String provider, String location) throws IOException {
        return DEFAULT.open(provider, location);
    }

    public static boolean exists(String provider, String location) throws IOException {
        StorageProvider selected = DEFAULT.provider(provider);
        return selected.exists(selected.normalizeLocation(location));
    }

    /** The parent repository owns this backend; only extension repositories are closed here. */
    public static StorageSession extensions(StorageBackend backend) {
        return DEFAULT.borrow(backend);
    }

    public StorageSession borrow(StorageBackend backend) {
        Shared shared = new Shared(backend, false);
        shared.references = 1;
        return new Lease(null, shared);
    }

    public synchronized StorageSession open(String provider, String location) throws IOException {
        StorageProvider selected = provider(provider);
        location = selected.normalizeLocation(location);
        String key = provider + "\u0000" + location;
        Shared shared = backends.get(key);
        if (shared == null) {
            shared = new Shared(selected.open(location));
            backends.put(key, shared);
        }
        shared.references++;
        return new Lease(key, shared);
    }

    private StorageProvider provider(String id) throws IOException {
        List<StorageProvider> matches = new ArrayList<>();
        for (StorageProvider provider : providers) {
            if (provider.id().equals(id)) {
                matches.add(provider);
            }
        }
        if (matches.size() != 1) {
            throw new IOException("Expected one storage provider for "
                                  + id
                                  + ", found "
                                  + matches.size());
        }
        return matches.get(0);
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
                        if (key != null) {
                            backends.remove(key);
                        }
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
        List<NoClassDefFoundError> unavailable = new ArrayList<>();
        for (RepositoryAdapter<?> adapter : adapters) {
            try {
                if (adapter.contract().equals(contract)
                        && adapter.supports(shared.backend.dialect())) {
                    matches.add(adapter);
                }
            } catch (NoClassDefFoundError missingOptionalContract) {
                unavailable.add(missingOptionalContract);
            }
        }
        if (matches.size() != 1) {
            IOException failure = new IOException("Expected one adapter for "
                                                  + contract.getName()
                                                  + " on "
                                                  + shared.backend.dialect()
                                                  + ", found "
                                                  + matches.size());
            unavailable.forEach(failure::addSuppressed);
            unavailableAdapters.forEach(failure::addSuppressed);
            throw failure;
        }
        RepositoryAdapter<?> adapter = matches.get(0);
        int version = shared.backend.schemaVersion(contract.getName());
        if (adapter.schemaVersion() < 1 || version != 0 && version != adapter.schemaVersion()) {
            throw new IOException("Incompatible storage schema for "
                                  + contract.getName()
                                  + ": stored "
                                  + version
                                  + ", required "
                                  + adapter.schemaVersion());
        }
        T repository = contract.cast(adapter.open(shared.backend));
        try {
            if (version == 0) {
                shared.backend.recordSchemaVersion(contract.getName(), adapter.schemaVersion());
            }
        } catch (IOException failure) {
            try {
                repository.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        shared.repositories.put(contract, repository);
        return repository;
    }

    private static final class Shared implements AutoCloseable {
        private final StorageBackend backend;
        private final boolean ownsBackend;
        private final Map<Class<?>, AutoCloseable> repositories = new LinkedHashMap<>();
        private int references;

        private Shared(StorageBackend backend) {
            this(backend, true);
        }

        private Shared(StorageBackend backend, boolean ownsBackend) {
            this.backend = backend;
            this.ownsBackend = ownsBackend;
        }

        @Override
        public void close() throws IOException {
            List<AutoCloseable> resources = new ArrayList<>(repositories.values());
            Collections.reverse(resources);
            if (ownsBackend) {
                resources.add(backend);
            }
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
