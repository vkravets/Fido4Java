package org.fidonet.fileecho.storage;

/** Immutable file area configuration, independent of its storage backend. */
public final class FileArea {
    public final String name;
    public final String directory;
    public final String description;
    public final boolean enabled;

    public FileArea(String name, String directory, String description, boolean enabled) {
        this.name = name;
        this.directory = directory;
        this.description = description;
        this.enabled = enabled;
    }
}
