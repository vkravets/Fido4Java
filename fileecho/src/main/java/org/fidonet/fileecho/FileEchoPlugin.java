package org.fidonet.fileecho;

import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.script.MailScriptExtension;

import java.io.IOException;
import java.lang.ref.WeakReference;

/** Optional provider; absence never prevents ordinary netmail and echomail processing. */
public final class FileEchoPlugin implements Plugin<MailScriptExtension>, MailScriptExtension {
    private WeakReference<MailScriptExtension> context;

    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("fileecho", 1, 0, "TIC file echo delivery");
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
    public WeakReference<MailScriptExtension> getContext() {
        return context;
    }

    @Override
    public void register(MailAutomationRuntime api) throws IOException {
        String enabled = api.getConfig().getValue("FileEchoEnabled", "true").trim();
        if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("FileEchoEnabled must be true or false");
        }
        if (Boolean.parseBoolean(enabled)) {
            FileEchoProcessor service = new FileEchoProcessor(api);
            api.registerFileEcho(service);
            api.registerSettingsExtension(new FileEchoSettings(api, service.store()));
            api.onScan(service::scan);
        }
    }
}
