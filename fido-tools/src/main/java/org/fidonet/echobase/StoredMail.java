package org.fidonet.echobase;

import org.fidonet.types.Message;

/** SQL row identity for cursor-based UI reads; distinct from the FTN MSGID. */
public final class StoredMail {
    private final long id;
    private final Message message;

    public StoredMail(long id, Message message) {
        this.id = id;
        this.message = message;
    }

    public long getId() {
        return id;
    }

    public Message getMessage() {
        return message;
    }
}
