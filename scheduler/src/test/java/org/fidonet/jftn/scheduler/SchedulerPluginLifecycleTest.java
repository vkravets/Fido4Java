package org.fidonet.jftn.scheduler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import org.fidonet.jftn.scheduler.plugin.SchedulerPlugin;

import org.junit.Test;

import java.lang.ref.WeakReference;

public class SchedulerPluginLifecycleTest {
    @Test
    public void unloadBeforeLoadAndRepeatedUnloadAreSafeAndReloadProvidesFreshScheduler()
            throws Exception {
        SchedulerPlugin plugin = new SchedulerPlugin();
        plugin.unload();
        plugin.load();
        WeakReference<Scheduler> first = plugin.getContext();
        Scheduler previous = first.get();
        assertNotNull(previous);
        plugin.unload();
        plugin.unload();
        assertNull(first.get());
        plugin.load();
        try {
            assertNotSame(previous, plugin.getContext().get());
        } finally {
            plugin.unload();
        }
    }
}
