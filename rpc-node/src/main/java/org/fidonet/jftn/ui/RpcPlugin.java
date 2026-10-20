package org.fidonet.jftn.ui;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.RuntimeProfile;
import org.fidonet.echobase.MailRepository;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.MailRuntime;
import org.fidonet.jftn.ServiceConfiguration;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.ServiceContext;
import org.fidonet.jftn.plugins.ServicePlugin;
import org.fidonet.jftn.poll.PollService;

/** Optional RPC owner: no browser resources or initial-setup dependency. */
public final class RpcPlugin implements ServicePlugin {
    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("rpc", 1, 0, "Authenticated TCP and HTTP node RPC");
    }

    @Override
    public boolean enabled(ServiceContext context) throws Exception {
        JFtnConfig config = ServiceConfiguration.read(context);
        boolean configured = ServiceConfiguration.configured(context);
        boolean enabled = ServiceConfiguration.flag(config, "RpcEnabled", !configured);
        RuntimeProfile profile = !configured
                ? context.find(RuntimeProfile.class).orElse(RuntimeProfile.read(config))
                : RuntimeProfile.read(config);
        if (enabled && !profile.usesSql()) {
            throw new IllegalArgumentException("RPC plugin requires SQL mail storage; select a SQL-backed installation or disable RpcEnabled");
        }
        return enabled;
    }

    @Override
    public AutoCloseable start(ServiceContext context) throws Exception {
        MailStorage storage = context.require(MailStorage.class);
        if (!(storage instanceof MailRepository)) {
            throw new IllegalArgumentException("RPC plugin requires SQL mail storage; select a SQL-backed installation");
        }
        return NodeRpc.open(context.require(JFtnConfig.class),
                            (MailRepository) storage,
                            context.require(MailRuntime.class),
                            context.require(SessionContext.class),
                            context.require(PollService.class));
    }
}
