package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.RuntimeProfile;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.ServiceContext;
import org.fidonet.jftn.plugins.ServicePlugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class NodePluginsTest {
    @TempDir
    Path temporary;

    @Test
    void requiredManualConfigurationWithoutASetupPlugin() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                      () -> NodePlugins.prepare(temporary.resolve("jftn.conf"),
                                                                                RuntimeProfile.FULL,
                                                                                List.of()));
        assertTrue(error.getMessage().contains("jftn.conf.example"));
        assertFalse(Files.exists(temporary.resolve("database")));
    }

    @Test
    void preparedBeforeRuntimeAndKeptTheSamePluginInstance() throws Exception {
        Path configuration = temporary.resolve("jftn.conf");
        ServicePlugin setup = new ServicePlugin() {
            @Override
            public PluginInformation getPluginInfo() {
                return new PluginInformation("setup", 1, 0, "Test setup");
            }

            @Override
            public void prepare(ServiceContext context) throws Exception {
                assertFalse(Files.exists(context.configuration()));
                Files.writeString(context.configuration(), "# Prepared before storage\n");
            }

            @Override
            public AutoCloseable start(ServiceContext context) {
                throw new AssertionError("Runtime must not start during setup");
            }
        };
        List<Plugin<?>> selected = NodePlugins.prepare(configuration,
                                                       RuntimeProfile.HEADLESS,
                                                       List.of(setup));
        assertSame(setup, selected.get(0));
        assertEquals("# Prepared before storage\n", Files.readString(configuration));
    }

    @Test
    void ignoredDisabledPluginDependenciesWithoutStartingIt() throws Exception {
        Path configuration = temporary.resolve("jftn.conf");
        Files.writeString(configuration, "# Operator configuration\n");
        ServicePlugin disabled = new ServicePlugin() {
            @Override
            public PluginInformation getPluginInfo() {
                return new PluginInformation("web-ui", 1, 0, "Disabled", "rpc");
            }

            @Override
            public boolean enabled(ServiceContext context) {
                return false;
            }

            @Override
            public AutoCloseable start(ServiceContext context) {
                throw new AssertionError("Disabled plugin must not start");
            }
        };
        assertTrue(NodePlugins.prepare(configuration, RuntimeProfile.FULL, List.of(disabled))
                              .isEmpty());
    }
}
