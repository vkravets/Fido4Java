package org.fidonet.jftn.ui.transport;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.RpcServiceProvider;
import org.fidonet.jftn.rpc.RpcTransport;
import org.fidonet.jftn.rpc.Server;

/** Existing framed binary TCP RPC configuration. */
public final class TcpRpcTransportFactory implements RpcTransportFactory {
    @Override
    public String name() {
        return "tcp";
    }

    @Override
    public RpcTransport create(JFtnConfig config, RpcServiceProvider handlers) {
        return new Server(Integer.parseInt(config.getValue("RpcPort", "9090")),
                          config.getValue("RpcBind", "127.0.0.1"),
                          handlers);
    }
}
