package org.fidonet.jftn.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.events.EventBus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PluginLifecycleTest {
    private final PluginManager manager = PluginManager.getInstance();
    private final List<String> calls = new ArrayList<>();

    @Before
    public void reset() {
        manager.unloadPlugins();
    }

    @After
    public void cleanup() {
        manager.unloadPlugins();
    }

    @Test
    public void initializesDependenciesBeforeDependantsAndClearsContextsOnUnload()
            throws Exception {
        TestPlugin dependency = new TestPlugin("dependency");
        TestPlugin child = new TestPlugin("child", "dependency");
        manager.loadPlugins(Arrays.asList(child, dependency));
        assertEquals(Arrays.asList("init dependency",
                                   "load dependency",
                                   "init child",
                                   "load child"),
                     calls);
        WeakReference<String> context = manager.getContext("child");
        assertEquals("child", context.get());
        PluginInformation info = child.getPluginInfo();
        assertEquals(1, info.getMajorVersion());
        assertEquals(2, info.getMinorVersion());
        assertEquals("test plugin", info.getDescription());
        manager.unloadPlugins();
        assertNull(context.get());
        assertEquals(Arrays.asList("unload child", "unload dependency"),
                     calls.subList(calls.size() - 2, calls.size()));
        assertTrue(manager.findContexts(String.class).isEmpty());
        try {
            manager.getContext("child");
            fail("Unloaded plugin still registered");
        } catch (PluginException expected) {
        }
    }

    @Test
    public void loadAndUnloadFailureDoNotPreventOtherPluginsFromBeingProcessed() {
        TestPlugin broken = new TestPlugin("broken");
        broken.failLoad = true;
        broken.failUnload = true;
        TestPlugin working = new TestPlugin("working");
        manager.loadPlugins(Arrays.asList(broken, working));
        assertTrue(calls.contains("load working"));
        assertEquals(Collections.singletonList("working"), manager.findContexts(String.class));
        manager.unloadPlugins();
        assertTrue(calls.contains("unload working"));
        try {
            manager.getContext("broken");
            fail("Registry not cleared after unload failure");
        } catch (PluginException expected) {
        }
    }

    @Test
    public void missingAndCircularDependenciesFailBeforePluginsAreLoaded() {
        for (List<Plugin> plugins : Arrays.asList(Collections.<Plugin>singletonList(new TestPlugin("missing",
                                                                                                   "absent")),
                                                  Arrays.<Plugin>asList(new TestPlugin("first",
                                                                                       "second"),
                                                                        new TestPlugin("second",
                                                                                       "first")),
                                                  Collections.<Plugin>singletonList(new TestPlugin("self",
                                                                                                   "self")))) {
            try {
                manager.loadPlugins(plugins);
                fail("Invalid dependencies accepted");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("dependency"));
            }
            assertTrue(calls.isEmpty());
        }
    }

    private class TestPlugin implements Plugin<String> {
        private final PluginInformation information;
        private final WeakReference<String> context;
        boolean failLoad;
        boolean failUnload;

        TestPlugin(String id, String... dependencies) {
            information = new PluginInformation(id, 1, 2, "test plugin", dependencies);
            context = new WeakReference<>(id);
        }

        @Override
        public PluginInformation getPluginInfo() {
            return information;
        }

        @Override
        public void init(PluginManager manager, EventBus bus) {
            calls.add("init " + information.getId());
        }

        @Override
        public void load() throws PluginException {
            calls.add("load " + information.getId());
            if (failLoad) {
                throw new PluginException("load failed");
            }
        }

        @Override
        public void unload() throws PluginException {
            calls.add("unload " + information.getId());
            context.clear();
            if (failUnload) {
                throw new PluginException("unload failed");
            }
        }

        @Override
        public WeakReference<String> getContext() {
            return context;
        }
    }
}
