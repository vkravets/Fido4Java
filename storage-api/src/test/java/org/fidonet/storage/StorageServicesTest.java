package org.fidonet.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

class StorageServicesTest {
    interface Names extends AutoCloseable {
        String read();

        @Override
        void close();
    }

    static class Backend implements StorageBackend {
        int closes;
        int schema;

        @Override
        public int schemaVersion(String module) {
            return schema;
        }

        @Override
        public void recordSchemaVersion(String module, int version) {
            schema = version;
        }

        @Override
        public String dialect() {
            return "test";
        }

        @Override
        public void close() {
            closes++;
        }
    }

    static final class Provider implements StorageProvider {
        final Backend backend = new Backend();
        int opens;

        @Override
        public String id() {
            return "test";
        }

        @Override
        public StorageBackend open(String location) {
            opens++;
            return backend;
        }
    }

    static final class Adapter implements RepositoryAdapter<Names> {
        int opens;
        int closes;

        @Override
        public Class<Names> contract() {
            return Names.class;
        }

        @Override
        public boolean supports(String dialect) {
            return dialect.equals("test");
        }

        @Override
        public Names open(StorageBackend backend) {
            opens++;
            return new Names() {
                @Override
                public String read() {
                    return "available";
                }

                @Override
                public void close() {
                    closes++;
                }
            };
        }
    }

    @Test
    void sharedRepositoryOutlivesIndividualClientsAndClosesOnce() throws Exception {
        Provider provider = new Provider();
        Adapter adapter = new Adapter();
        StorageServices services = new StorageServices(List.of(provider), List.of(adapter));
        StorageSession first = services.open("test", "same");
        try (StorageSession second = services.open("test", "same")) {
            Names repository = first.require(Names.class);
            assertSame(repository, second.require(Names.class));
            first.close();
            first.close();
            assertEquals("available", second.require(Names.class).read());
            assertEquals(0, provider.backend.closes);
            assertThrows(IOException.class, () -> first.require(Names.class));
        }
        assertEquals(1, provider.opens);
        assertEquals(1, adapter.opens);
        assertEquals(1, adapter.closes);
        assertEquals(1, provider.backend.closes);
    }

    @Test
    void missingOrAmbiguousProvidersAndAdaptersFailExplicitly() throws Exception {
        Provider provider = new Provider();
        Adapter adapter = new Adapter();
        assertThrows(IOException.class,
                     () -> new StorageServices(List.of(), List.of()).open("unknown", "file"));
        assertThrows(IOException.class,
                     () -> new StorageServices(List.of(provider, provider),
                                               List.of()).open("test", "file"));
        try (StorageSession session = new StorageServices(List.of(provider),
                                                          List.of()).open("test", "file")) {
            assertThrows(IOException.class, () -> session.require(Names.class));
        }
        try (StorageSession session = new StorageServices(List.of(provider),
                                                          List.of(adapter, adapter)).open("test",
                                                                                          "file")) {
            assertThrows(IOException.class, () -> session.require(Names.class));
        }
    }

    @Test
    void unsupportedDialectDoesNotFallBackToAnotherBackend() throws Exception {
        Provider provider = new Provider();
        RepositoryAdapter<Names> adapter = new AdapterForOtherDialect();
        try (StorageSession session = new StorageServices(List.of(provider),
                                                          List.of(adapter)).open("test", "file")) {
            assertThrows(IOException.class, () -> session.require(Names.class));
        }
    }

    @Test
    void incompatibleSchemaIsRejectedBeforeOpeningARepository() throws Exception {
        Provider provider = new Provider();
        provider.backend.schema = 2;
        Adapter adapter = new Adapter();
        try (StorageSession session = new StorageServices(List.of(provider),
                                                          List.of(adapter)).open("test", "file")) {
            assertThrows(IOException.class, () -> session.require(Names.class));
            assertEquals(0, adapter.opens);
            assertEquals(2, provider.backend.schema);
        }
    }

    @Test
    void closingAnExtensionRegistryDoesNotCloseItsParentBackend() throws Exception {
        Backend backend = new Backend();
        Adapter adapter = new Adapter();
        StorageServices services = new StorageServices(List.of(), List.of(adapter));
        try (StorageSession session = services.borrow(backend)) {
            assertEquals("available", session.require(Names.class).read());
        }
        assertEquals(1, adapter.closes);
        assertEquals(0, backend.closes);
    }

    @Test
    void anAbsentOptionalPluginContractDoesNotBreakUnrelatedRepositories() throws Exception {
        RepositoryAdapter<Names> absent = new RepositoryAdapter<Names>() {
            @Override
            public Class<Names> contract() {
                throw new NoClassDefFoundError("RemovedOptionalPluginContract");
            }

            @Override
            public boolean supports(String dialect) {
                return true;
            }

            @Override
            public Names open(StorageBackend backend) {
                throw new AssertionError("An absent plugin must not be opened");
            }
        };
        StorageServices services = new StorageServices(List.of(new Provider()),
                                                       List.of(absent, new Adapter()));
        try (StorageSession session = services.open("test", "file")) {
            assertEquals("available", session.require(Names.class).read());
        }
    }

    @Test
    void unchangedClientCodeUsesAnAdapterForAnotherBackend() throws Exception {
        StorageProvider provider = new StorageProvider() {
            @Override
            public String id() {
                return "other";
            }

            @Override
            public StorageBackend open(String location) {
                return new Backend() {
                    @Override
                    public String dialect() {
                        return "other";
                    }
                };
            }
        };
        StorageServices services = new StorageServices(List.of(provider),
                                                       List.of(new AdapterForOtherDialect()));
        try (StorageSession session = services.open("other", "opaque-location")) {
            assertEquals("other-provider", session.require(Names.class).read());
        }
    }

    static final class AdapterForOtherDialect implements RepositoryAdapter<Names> {
        @Override
        public Class<Names> contract() {
            return Names.class;
        }

        @Override
        public boolean supports(String dialect) {
            return dialect.equals("other");
        }

        @Override
        public Names open(StorageBackend backend) {
            return new Names() {
                @Override
                public String read() {
                    return "other-provider";
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
