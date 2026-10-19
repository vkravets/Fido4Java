package org.fidonet.jftn;

import org.fidonet.config.RuntimeProfile;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.ServiceContext;
import org.fidonet.jftn.plugins.ServicePlugins;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/** Discovers plugins once, prepares optional setup, then selects configured services. */
public final class NodePlugins {
    private NodePlugins() {
    }

    public static List<Plugin<?>> prepare(Path configuration, RuntimeProfile profile)
            throws Exception {
        List<Plugin<?>> installed = new ArrayList<>();
        for (Plugin<?> plugin : ServiceLoader.load(Plugin.class)) {
            installed.add(plugin);
        }
        return prepare(configuration, profile, installed);
    }

    static List<Plugin<?>> prepare(Path configuration,
                                   RuntimeProfile profile,
                                   List<Plugin<?>> installed)
            throws Exception {
        ServiceContext context = new ServiceContext(configuration).provide(RuntimeProfile.class,
                                                                           profile);
        ServicePlugins services = ServicePlugins.fromPlugins(installed);
        services.prepare(context);
        if (!Files.isRegularFile(configuration)) {
            throw new IllegalArgumentException("Create jftn.conf from jftn.conf.example before starting; no enabled setup plugin created the configuration");
        }
        Set<String> disabled = services.disabledIds(context);
        List<Plugin<?>> selected = new ArrayList<>();
        for (Plugin<?> plugin : installed) {
            String id = plugin.getPluginInfo().getId();
            if (!disabled.contains(id) && storageCompatible(id, profile)) {
                selected.add(plugin);
            }
        }
        return selected;
    }

    private static boolean storageCompatible(String id, RuntimeProfile profile) {
        return profile.usesSql() || !Set.of("database", "jam-bridge", "nodelist").contains(id);
    }
}
