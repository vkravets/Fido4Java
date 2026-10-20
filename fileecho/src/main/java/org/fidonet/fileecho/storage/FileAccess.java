package org.fidonet.fileecho.storage;

/** Immutable access grant for one area and peer. */
public final class FileAccess {
    public final String area;
    public final String peer;
    public final String access;

    public FileAccess(String area, String peer, String access) {
        this.area = area;
        this.peer = peer;
        this.access = access;
    }
}
