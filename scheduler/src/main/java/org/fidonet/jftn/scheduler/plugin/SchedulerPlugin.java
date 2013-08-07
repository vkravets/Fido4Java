package org.fidonet.jftn.scheduler.plugin;

import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginException;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.jftn.scheduler.Scheduler;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/6/13
 * Time: 2:46 PM
 */
public class SchedulerPlugin implements Plugin {

    private Scheduler scheduler;

    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("schedule", 1, 0, "Cron-like scheduler functionality plugin");
    }

    @Override
    public void init(PluginManager manager, EventBus eventBus) {
    }

    @Override
    public void load() throws PluginException {
        scheduler = new Scheduler();
    }

    @Override
    public void unload() throws PluginException {
        scheduler = null;
    }

    @Override
    public Object getContext() {
        return scheduler;
    }
}
