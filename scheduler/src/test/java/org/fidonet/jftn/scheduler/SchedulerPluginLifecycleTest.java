package org.fidonet.jftn.scheduler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.fidonet.jftn.scheduler.plugin.SchedulerPlugin;

import org.junit.jupiter.api.Test;

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
