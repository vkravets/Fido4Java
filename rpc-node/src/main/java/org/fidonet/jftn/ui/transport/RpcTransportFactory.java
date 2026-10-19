package org.fidonet.jftn.ui.transport;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.RpcServiceProvider;
import org.fidonet.jftn.rpc.RpcTransport;

/** Creates a configured listener; null means this transport is disabled. */
public interface RpcTransportFactory {
    String name();

    RpcTransport create(JFtnConfig config, RpcServiceProvider handlers) throws Exception;
}
