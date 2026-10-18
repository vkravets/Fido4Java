package org.fidonet.binkp.common.io;

/** Mutable progress owned by one file offer, excluded from wire identity and map keys. */
final class FileTransferState {
    volatile long currentSize;
    volatile long offset;
    volatile boolean finished;
    volatile boolean skip;
}
