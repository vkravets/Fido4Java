package org.fidonet.jftn.rpc;

/** Lifecycle shared by RPC listeners, independently of their wire protocol. */
public interface RpcTransport extends AutoCloseable {
    void run();

    int getLocalPort();

    @Override
    void close();
}
