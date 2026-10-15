package org.fidonet.echobase;

import java.util.List;

/** Durable route administration for CLI/RPC callers. */
public interface NetmailRoutingStore {
    List<NetmailRoute> getNetmailRoutes();
    void saveNetmailRoute(NetmailRoute route);
    void removeNetmailRoute(String id);
}
