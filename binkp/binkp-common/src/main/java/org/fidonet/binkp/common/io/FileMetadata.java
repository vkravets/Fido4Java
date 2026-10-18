package org.fidonet.binkp.common.io;

/** Immutable wire identity, independent of a transfer's current progress. */
public final class FileMetadata {
    private final String name;
    private final long size;
    private final long timestamp;

    public FileMetadata(String name, long size, long timestamp) {
        this.name = name;
        this.size = size;
        this.timestamp = timestamp;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FileMetadata))
            return false;
        FileMetadata metadata = (FileMetadata) other;
        return size == metadata.size && timestamp == metadata.timestamp
                && name.equals(metadata.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) (size ^ (size >>> 32));
        return 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    }
}
