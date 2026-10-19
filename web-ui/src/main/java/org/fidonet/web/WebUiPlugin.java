package org.fidonet.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.ServiceConfiguration;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.ServiceContext;
import org.fidonet.jftn.plugins.ServicePlugin;
import org.fidonet.jftn.web.InitialSetup;

/** Browser feature; its HTTP routes are contributed to the RPC listener. */
public final class WebUiPlugin implements ServicePlugin {
    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("web-ui", 1, 0, "Browser administration", "rpc");
    }

    @Override
    public boolean enabled(ServiceContext context) throws Exception {
        JFtnConfig config = ServiceConfiguration.read(context);
        return config == null || ServiceConfiguration.flag(config, "WebUiEnabled", true)
                && ServiceConfiguration.flag(config, "RpcEnabled", false);
    }

    @Override
    public void prepare(ServiceContext context) throws Exception {
        JFtnConfig config = ServiceConfiguration.read(context);
        if (config != null) {
            validateHttp(config);
        }
        InitialSetup.prepare(context.configuration());
    }

    @Override
    public AutoCloseable start(ServiceContext context) {
        JFtnConfig config = context.require(JFtnConfig.class);
        validateHttp(config);
        return () -> {
        };
    }

    private void validateHttp(JFtnConfig config) {
        if (!ServiceConfiguration.flag(config, "RpcHttpEnabled", false)) {
            throw new IllegalArgumentException("Web UI requires RpcHttpEnabled=true; otherwise set WebUiEnabled=false");
        }
    }
}
