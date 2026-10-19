package org.fidonet.jftn.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/** Orders enabled services and owns resources in reverse startup order. */
public final class ServicePlugins implements AutoCloseable {
    private final List<ServicePlugin> plugins;
    private final List<AutoCloseable> resources = new ArrayList<>();
    private boolean started;

    public ServicePlugins(Collection<? extends ServicePlugin> plugins) {
        this.plugins = List.copyOf(plugins);
    }

    public static ServicePlugins discover() {
        List<Plugin<?>> plugins = new ArrayList<>();
        for (Plugin<?> plugin : ServiceLoader.load(Plugin.class)) {
            plugins.add(plugin);
        }
        return fromPlugins(plugins);
    }

    public static ServicePlugins fromPlugins(Iterable<? extends Plugin<?>> plugins) {
        List<ServicePlugin> result = new ArrayList<>();
        for (Plugin<?> plugin : plugins) {
            if (plugin instanceof ServicePlugin) {
                result.add((ServicePlugin) plugin);
            }
        }
        return new ServicePlugins(result);
    }

    public Set<String> disabledIds(ServiceContext context) throws Exception {
        Set<String> result = new HashSet<>();
        for (ServicePlugin plugin : plugins) {
            if (!plugin.enabled(context)) {
                result.add(plugin.getPluginInfo().getId());
            }
        }
        return result;
    }

    public void prepare(ServiceContext context) throws Exception {
        for (ServicePlugin plugin : ordered(context)) {
            plugin.prepare(context);
        }
    }

    public void start(ServiceContext context) throws Exception {
        if (started) {
            throw new IllegalStateException("Node service plugins were already started");
        }
        started = true;
        try {
            for (ServicePlugin plugin : ordered(context)) {
                AutoCloseable resource = start(plugin, context);
                if (resource != null) {
                    resources.add(resource);
                }
            }
        } catch (Exception failure) {
            try {
                close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private AutoCloseable start(ServicePlugin plugin, ServiceContext context) throws Exception {
        try {
            return plugin.start(context);
        } catch (LinkageError missingLibrary) {
            throw new IllegalStateException("Unable to start service plugin "
                    + plugin.getPluginInfo().getId()
                    + ": a required library is missing or incompatible", missingLibrary);
        }
    }

    private List<ServicePlugin> ordered(ServiceContext context) throws Exception {
        Map<String, ServicePlugin> enabled = new LinkedHashMap<>();
        for (ServicePlugin plugin : plugins) {
            if (plugin.enabled(context)
                    && enabled.putIfAbsent(plugin.getPluginInfo().getId(), plugin) != null) {
                throw new IllegalArgumentException("Duplicate service plugin: "
                                                   + plugin.getPluginInfo()
                                                           .getId());
            }
        }
        List<ServicePlugin> result = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : enabled.keySet()) {
            visit(id, enabled, visiting, visited, result);
        }
        return result;
    }

    private void visit(String id,
                       Map<String, ServicePlugin> enabled,
                       Set<String> visiting,
                       Set<String> visited,
                       List<ServicePlugin> result) {
        if (visited.contains(id)) {
            return;
        }
        ServicePlugin plugin = enabled.get(id);
        if (plugin == null) {
            throw new IllegalArgumentException("Required service plugin is missing or disabled: "
                                               + id);
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Circular service plugin dependency: " + id);
        }
        for (String dependency : plugin.getPluginInfo().getDependencies()) {
            visit(dependency, enabled, visiting, visited, result);
        }
        visiting.remove(id);
        visited.add(id);
        result.add(plugin);
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        List<AutoCloseable> closing = new ArrayList<>(resources);
        resources.clear();
        Collections.reverse(closing);
        for (AutoCloseable resource : closing) {
            try {
                resource.close();
            } catch (Exception cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else {
                    failure.addSuppressed(cleanup);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
