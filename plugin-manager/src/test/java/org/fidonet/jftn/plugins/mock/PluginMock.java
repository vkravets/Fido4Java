package org.fidonet.jftn.plugins.mock;

import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginException;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/7/13
 * Time: 1:43 PM
 */
public abstract class PluginMock implements Plugin {
    @Override
    public abstract PluginInformation getPluginInfo();

    @Override
    public void init(PluginManager manager, EventBus eventBus) {

    }

    @Override
    public void load() throws PluginException {

    }

    @Override
    public void unload() throws PluginException {

    }

    @Override
    public Object getContext() {
        return null;
    }
}
