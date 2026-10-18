package org.fidonet.nodelist;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.tosser.MailProcessingExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.util.Optional;

/** Optional directory factory, discovered through the existing plugin and mail extension SPIs. */
public final class DirectoryPlugin implements Plugin<MailExtensionFactory>, MailExtensionFactory {
    private WeakReference<MailExtensionFactory> context;

    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("nodelist", 1, 0, "FTSC node and point directory import");
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
    public Optional<MailProcessingExtension> create(JFtnConfig config, MailDatabaseFacade base) {
        String enabled = config.getValue("NodeDirectoryEnabled", "false").trim();
        if (enabled.equalsIgnoreCase("false")) {
            return Optional.empty();
        }
        if (!enabled.equalsIgnoreCase("true")) {
            throw new IllegalArgumentException("NodeDirectoryEnabled must be true or false");
        }
        try {
            DirectoryService service = DirectoryService.open(config);
            try {
                service.refresh();
                return Optional.of(service);
            } catch (IOException | RuntimeException failure) {
                service.close();
                throw failure;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to initialize node directory", e);
        }
    }
}
