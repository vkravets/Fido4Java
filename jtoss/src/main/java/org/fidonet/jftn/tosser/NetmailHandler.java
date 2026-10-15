package org.fidonet.jftn.tosser;

import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;

/** Called synchronously for locally addressed netmail after packet authentication and storage. */
@FunctionalInterface
public interface NetmailHandler {
    /** Return true to consume the request; throw IOException to retain the packet for retry. */
    boolean handle(Message message, Link packetPeer, String deliveryKey) throws IOException;
}
