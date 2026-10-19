package org.fidonet.jftn.ui.transport;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.RpcServiceProvider;
import org.fidonet.jftn.rpc.RpcTransport;
import org.fidonet.jftn.ui.RpcConfiguration;

import java.io.IOException;

/** Browser HTTP/JSON RPC configuration. */
public final class HttpRpcTransportFactory implements RpcTransportFactory {
    @Override
    public String name() {
        return "http";
    }

    @Override
    public RpcTransport create(JFtnConfig config, RpcServiceProvider handlers) throws IOException {
        if (!RpcConfiguration.enabled(config, "RpcHttpEnabled")) {
            return null;
        }
        return new HttpRpcServer(Integer.parseInt(config.getValue("RpcHttpPort", "9091")),
                                 config.getValue("RpcHttpBind", "127.0.0.1"),
                                 handlers,
                                 RpcConfiguration.origins(config, "RpcHttpOrigin"),
                                 config);
    }
}
