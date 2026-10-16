package org.fidonet.jftn.ui.transport;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.RpcTransport;
import org.fidonet.jftn.rpc.ServerHandler;

/** Creates a configured listener; null means this transport is disabled. */
public interface RpcTransportFactory {
    String name();

    RpcTransport create(JFtnConfig config, ServerHandler handlers) throws Exception;
}
