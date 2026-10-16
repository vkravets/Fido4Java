package org.fidonet.jftn.ui.transport;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.RpcTransport;
import org.fidonet.jftn.rpc.ServerHandler;
import org.fidonet.jftn.ui.RpcResources;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Starts configured transports and closes all acquired listeners in reverse order. */
public final class RpcTransports implements AutoCloseable {
    private final Map<String, RpcTransport> listeners = new LinkedHashMap<>();
    private final RpcResources resources = new RpcResources();

    private RpcTransports() {
    }

    public static RpcTransports open(JFtnConfig config, ServerHandler handlers) throws Exception {
        return open(config,
                    handlers,
                    List.of(new TcpRpcTransportFactory(), new HttpRpcTransportFactory()));
    }

    public static RpcTransports open(JFtnConfig config,
                                     ServerHandler handlers,
                                     List<? extends RpcTransportFactory> factories)
            throws Exception {
        validateNames(factories);
        RpcTransports transports = new RpcTransports();
        try {
            transports.startAll(config, handlers, factories);
            return transports;
        } catch (Exception failure) {
            transports.resources.closeAfterFailure(failure);
            throw failure;
        }
    }

    private void startAll(JFtnConfig config,
                          ServerHandler handlers,
                          List<? extends RpcTransportFactory> factories)
            throws Exception {
        for (RpcTransportFactory factory : factories) {
            start(config, handlers, factory);
        }
    }

    private static void validateNames(List<? extends RpcTransportFactory> factories) {
        Set<String> names = new HashSet<>();
        for (RpcTransportFactory factory : factories) {
            String name = factory.name();
            if (name == null || name.isBlank() || !names.add(name)) {
                throw new IllegalArgumentException("RPC transport names must be nonempty and unique");
            }
        }
    }

    private void start(JFtnConfig config, ServerHandler handlers, RpcTransportFactory factory)
            throws Exception {
        RpcTransport listener = factory.create(config, handlers);
        if (listener == null) {
            return;
        }
        resources.add(listener);
        listeners.put(factory.name(), listener);
        listener.run();
    }

    public synchronized int getPort(String name) {
        RpcTransport listener = listeners.get(name);
        return listener == null ? -1 : listener.getLocalPort();
    }

    @Override
    public synchronized void close() {
        listeners.clear();
        resources.close();
    }
}
