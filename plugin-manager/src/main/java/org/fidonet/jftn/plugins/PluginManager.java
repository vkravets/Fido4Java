/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.jftn.plugins;

import org.fidonet.events.HasEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 9:05 PM
 */
public class PluginManager extends HasEventBus {
    private static PluginManager ourInstance;

    public static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

    public static PluginManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new PluginManager();
        }
        return ourInstance;
    }

    private static final Map<String, Plugin> plugins = new HashMap<String, Plugin>();

    private PluginManager() {

    }

    public void loadPlugins() {
        Iterable<? extends Plugin> plugins = ServiceLoader.load(Plugin.class);

        // Register all plugins before it's loading 
        for (Plugin plugin : plugins) {
            addPlugin(plugin.getPluginInfo().getId(), plugin);
        }

        // sort plugins for loading according to its dependencies 
        Collection<? extends Plugin> sortedPlugins = sortDependencies(plugins);

        // After loading all plugins, try to init and load it
        for (Plugin plugin : sortedPlugins) {
            String pluginId = plugin.getPluginInfo().getId();
            try {
                long startTime = System.currentTimeMillis();
                logger.info("Loading {} plugin", pluginId);
                plugin.init(this, getEventBus());
                plugin.load();
                logger.info("Plugin {} was successfully loaded", pluginId);
                logger.debug("Plugin {} loading time is {}ms", pluginId, System.currentTimeMillis() - startTime);
            } catch (PluginException e) {
                logger.error("Unable to load {} plugin", pluginId, e);
            }

        }
    }

    private Collection<? extends Plugin> sortDependencies(Iterable<? extends Plugin> plugins) {
        Set<Plugin> result = new LinkedHashSet<>();

        Map<String, Plugin> mapPlugins = new HashMap<>();
        for (Plugin plugin : plugins) {
            mapPlugins.put(plugin.getPluginInfo().getId(), plugin);
        }

        Set<Plugin> firstPlugins = new LinkedHashSet<>();
        Set<Plugin> pluginsWithDeps = new LinkedHashSet<>();
        for (Plugin plugin : plugins) {
            Collection<? extends Plugin> dependencies = buildDependencies(plugin, mapPlugins, firstPlugins);
            if (!dependencies.isEmpty()) {
                pluginsWithDeps.addAll(dependencies);
                pluginsWithDeps.add(plugin);
            } else {
                firstPlugins.add(plugin);
            }
        }

        result.addAll(firstPlugins);
        result.addAll(pluginsWithDeps);
        return result;
    }

    private Collection<? extends Plugin> buildDependencies(Plugin plugin, Map<String, Plugin> pluginMap, Set<Plugin> firstPlugins) {
        LinkedHashSet<Plugin> result = new LinkedHashSet<>();
        List<String> dependencies = plugin.getPluginInfo().getDependencies();
        for (String dependency : dependencies) {
            Plugin depPlugin = pluginMap.get(dependency);
            Collection<? extends Plugin> depPlugins = buildDependencies(depPlugin, pluginMap, firstPlugins);
            if (!depPlugins.isEmpty()) {
                result.addAll(depPlugins);
                result.add(depPlugin);
            } else {
                firstPlugins.add(depPlugin);
            }
        }
        return result;
    }

    public void unloadPlugins() {
        for (Plugin plugin : getPlugins().values()) {
            try {
                plugin.unload();
                logger.debug("Plugin {} was successfully unloaded", plugin.getPluginInfo().getId());
            } catch (PluginException e) {
                logger.error("Unable to unload {} plugin", plugin.getPluginInfo().getId(), e);
            }
        }
        plugins.clear();
    }

    private static void addPlugin(String id, Plugin plugin) {
        plugins.put(id, plugin);
    }

    private static void removePlugin(String id) {
        plugins.remove(id);
    }

    private static Map<String, Plugin> getPlugins() {
        return plugins;
    }

    public <T> WeakReference<T> getContext(String id) throws PluginException {
        Plugin plugin = plugins.get(id);
        if (plugin == null) throw new PluginException("Plugin \"" + id + "\" is not found or is not loaded");
        WeakReference<T> context = (WeakReference<T>) plugin.getContext();
        return context;
    }
}
