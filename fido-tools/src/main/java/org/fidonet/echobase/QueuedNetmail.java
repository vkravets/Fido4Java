package org.fidonet.echobase;

import org.fidonet.types.Message;

public final class QueuedNetmail {
    private final String key;
    private final String nextHop;
    private final Message message;

    public QueuedNetmail(String key, String nextHop, Message message) {
        this.key = key;
        this.nextHop = nextHop;
        this.message = message;
    }

    public String getKey() {
        return key;
    }

    public String getNextHop() {
        return nextHop;
    }

    public Message getMessage() {
        return message;
    }
}
