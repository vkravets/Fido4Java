package org.fidonet.jftn.tosser.jam;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.MailStorage;
import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Optional;

/** Discovers a factory; each mail service owns its configured bridge instance. */
public final class JamBridgePlugin implements Plugin<MailExtensionFactory>, MailExtensionFactory {
    private WeakReference<MailExtensionFactory> context;

    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("jam-bridge", 1, 0, "JAM editor synchronization", "database");
    }

    @Override
    public void init(PluginManager manager, EventBus eventBus) {
    }

    @Override
    public void load() {
        context = new WeakReference<>(this);
    }

    @Override
    public void unload() {
        if (context != null) {
            context.clear();
        }
    }

    @Override
    public WeakReference<MailExtensionFactory> getContext() {
        return context;
    }

    @Override
    public Optional<MailProcessingExtension> create(JFtnConfig config, MailStorage base) {
        String enabled = config.getValue("JamBridgeEnabled", "true").trim();
        if ("false".equalsIgnoreCase(enabled)) {
            return Optional.empty();
        }
        if (!"true".equalsIgnoreCase(enabled)) {
            throw new IllegalArgumentException("JamBridgeEnabled must be true or false");
        }
        JamBridge bridge = new JamBridge(config, base, base);
        return Optional.of(new MailProcessingExtension() {
            @Override
            public void beforeExport(Tosser tosser) throws IOException {
                bridge.importOutgoing(tosser);
            }

            @Override
            public void afterExport() throws IOException {
                bridge.exportMessages();
                bridge.reconcileDeliveries();
            }
        });
    }
}
