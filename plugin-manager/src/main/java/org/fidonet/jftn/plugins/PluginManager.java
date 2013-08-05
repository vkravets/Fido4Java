/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.jftn.plugins;

import org.fidonet.events.HasEventBus;
import org.openide.util.Lookup;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 9:05 PM
 */
public class PluginManager extends HasEventBus {
    private static PluginManager ourInstance = new PluginManager();

    public static PluginManager getInstance() {
        return ourInstance;
    }

    private static final Map<String, Plugin> plugins = Collections.emptyMap();
    
    private PluginManager() {
        
    }
    
    public void loadPlugins() {
        Lookup lookupService = Lookup.getDefault();
        Lookup.Template<Plugin> pluginTemplate = new Lookup.Template<Plugin>(Plugin.class);
        Lookup.Result<Plugin> pluginResult = lookupService.lookup(pluginTemplate);
        Collection<? extends Plugin> plugins = pluginResult.allInstances();
        for (Plugin plugin : plugins) {
            try {
                addPlugin(plugin.getPluginInfo().getId(), plugin);
                plugin.init(this, getEventBus());
                plugin.load();
            } catch (PluginException e) {
                // TODO: logger
            }
        }
    }
    
    public void unloadPlugins() throws PluginException {
        for (Plugin plugin : getPlugins().values()) {
            plugin.unload();
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
}
