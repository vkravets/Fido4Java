package org.fidonet.echobase;

import org.fidonet.db.robots.RobotRules;
import org.fidonet.types.Link;

/** Mail processing capabilities shared by SQL and durable file stores. */
public interface MailStorage
        extends
            IBase,
            NetmailStore,
            EchoRoutingStore,
            IncrementalEchoStore,
            AutoCloseable {
    void configureLink(Link link, String packetPassword);

    void configureSubscription(Link link, String area, AccessLevel access);

    RobotRules getRobotRules();
}
