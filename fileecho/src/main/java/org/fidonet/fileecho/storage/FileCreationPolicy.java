package org.fidonet.fileecho.storage;

/** Immutable automatic creation policy for a configured peer. */
public final class FileCreationPolicy {
    public final String peer;
    public final String masks;
    public final String access;

    public FileCreationPolicy(String peer, String masks, String access) {
        this.peer = peer;
        this.masks = masks;
        this.access = access;
    }
}
