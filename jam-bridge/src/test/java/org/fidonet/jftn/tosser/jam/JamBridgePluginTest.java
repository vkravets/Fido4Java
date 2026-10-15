package org.fidonet.jftn.tosser.jam;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.MailExtensionFactory;

import org.junit.Test;

import java.lang.ref.WeakReference;

public class JamBridgePluginTest {
    @Test
    public void publishesFactoryAndClearsItsContextOnUnload() {
        JamBridgePlugin plugin = new JamBridgePlugin();
        plugin.load();
        WeakReference<MailExtensionFactory> context = plugin.getContext();
        assertSame(plugin, context.get());
        plugin.unload();
        assertNull(context.get());
    }

    @Test
    public void disabledFactoryDoesNotReadJamConfigurationOrDatabase() {
        JFtnConfig config = new JFtnConfig() {
            @Override
            public String getValue(String name, String defaultValue) {
                if (!"JamBridgeEnabled".equals(name)) {
                    throw new AssertionError("Disabled plugin read " + name);
                }
                return "false";
            }
        };
        assertFalse(new JamBridgePlugin().create(config, null).isPresent());
    }

    @Test
    public void rejectsMisspelledEnableFlagBeforeCreatingBridge() {
        JFtnConfig config = new JFtnConfig() {
            @Override
            public String getValue(String name, String defaultValue) {
                return "treu";
            }
        };
        try {
            new JamBridgePlugin().create(config, null);
            fail("Invalid flag accepted");
        } catch (IllegalArgumentException expected) {
            assertSame(IllegalArgumentException.class, expected.getClass());
        }
    }
}
