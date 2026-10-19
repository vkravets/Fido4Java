package org.fidonet.jftn.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class ServicePluginsTest {
    private final List<String> events = new ArrayList<>();
    private final ServiceContext context = new ServiceContext(Path.of("jftn.conf"));

    private ServicePlugin plugin(String id, boolean enabled, boolean fail, String... dependencies) {
        return new ServicePlugin() {
            @Override
            public PluginInformation getPluginInfo() {
                return new PluginInformation(id, 1, 0, "Test", dependencies);
            }

            @Override
            public boolean enabled(ServiceContext context) {
                return enabled;
            }

            @Override
            public void prepare(ServiceContext context) {
                events.add("prepare:" + id);
            }

            @Override
            public AutoCloseable start(ServiceContext context) throws IOException {
                events.add("start:" + id);
                if (fail) {
                    throw new IOException("Startup failed");
                }
                return () -> events.add("close:" + id);
            }
        };
    }

    @Test
    void preparedAndStartedDependenciesAndClosedInReverseOrder() throws Exception {
        ServicePlugins plugins = new ServicePlugins(List.of(plugin("ui", true, false, "rpc"),
                                                            plugin("rpc", true, false)));
        plugins.prepare(context);
        plugins.start(context);
        plugins.close();
        plugins.close();
        assertEquals(List.of("prepare:rpc",
                             "prepare:ui",
                             "start:rpc",
                             "start:ui",
                             "close:ui",
                             "close:rpc"),
                     events);
        assertThrows(IllegalStateException.class, () -> plugins.start(context));
    }

    @Test
    void rejectedMissingOrDisabledDependenciesBeforeSideEffects() {
        ServicePlugins plugins = new ServicePlugins(List.of(plugin("ui", true, false, "rpc"),
                                                            plugin("rpc", false, false)));
        assertThrows(IllegalArgumentException.class, () -> plugins.prepare(context));
        assertEquals(List.of(), events);
    }

    @Test
    void cleanedUpPreviouslyStartedServicesWhenLaterStartupFailed() {
        ServicePlugins plugins = new ServicePlugins(List.of(plugin("rpc", true, false),
                                                            plugin("ui", true, true, "rpc")));
        assertThrows(IOException.class, () -> plugins.start(context));
        assertEquals(List.of("start:rpc", "start:ui", "close:rpc"), events);
    }

    @Test
    void skippedDisabledServicesAndDetectedCycles() throws Exception {
        new ServicePlugins(List.of(plugin("ui", false, true, "missing"))).start(context);
        assertEquals(List.of(), events);
        ServicePlugins plugins = new ServicePlugins(List.of(plugin("ui", true, false, "rpc"),
                                                            plugin("rpc", true, false, "ui")));
        assertThrows(IllegalArgumentException.class, () -> plugins.prepare(context));
    }

    @Test
    void RequiredCapabilitiesWereExplicitAndDuplicateBindingsRejected() {
        assertThrows(IllegalStateException.class, () -> context.require(String.class));
        context.provide(String.class, "service");
        assertEquals("service", context.require(String.class));
        assertThrows(IllegalArgumentException.class, () -> context.provide(String.class, "other"));
    }

    @Test
    void continuedShutdownAfterFailureAndRetainedSuppressedExceptions() throws Exception {
        ServiceContext resources = new ServiceContext(Path.of("jftn.conf"));
        ServicePlugin plugin = new ServicePlugin() {
            @Override
            public PluginInformation getPluginInfo() {
                return new PluginInformation("failing", 1, 0, "Test");
            }

            @Override
            public AutoCloseable start(ServiceContext context) {
                return () -> {
                    throw new IOException("Close failed");
                };
            }
        };
        ServicePlugins plugins = new ServicePlugins(List.of(plugin("rpc", true, false), plugin));
        plugins.start(resources);
        assertThrows(IOException.class, plugins::close);
        assertEquals(List.of("start:rpc", "close:rpc"), events);
    }
}
