package org.fidonet.echobase;

import org.fidonet.types.Message;

/** Imports historical messages without authorizing network distribution. */
public interface ArchiveStore {
    boolean importArchive(Message message, String source);
}
