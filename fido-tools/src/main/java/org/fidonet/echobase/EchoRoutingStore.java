package org.fidonet.echobase;

import org.fidonet.types.Link;

import java.io.IOException;

/** Subscription administration and explicit history reads, separate from delivery cursors. */
public interface EchoRoutingStore {
    void configureEchoSubscription(Link link, String area, AccessLevel access, EchoHistory history);
    void removeEchoSubscription(Link link, String area);
    void visitEchoRange(String area, long firstId, long lastId, MessageIteration.Handler handler)
            throws IOException;
}
