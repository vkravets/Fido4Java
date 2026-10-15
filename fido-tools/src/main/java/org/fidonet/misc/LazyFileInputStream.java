package org.fidonet.misc;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Opens a queued file on first use and supports bounded-memory retransmission. */
public final class LazyFileInputStream extends InputStream {
    private final Path path;
    private FileInputStream input;
    private long position;
    private long marked;
    private boolean closed;

    public LazyFileInputStream(Path path) {
        this.path = path;
    }

    private FileInputStream input() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        if (input == null) {
            input = new FileInputStream(path.toFile());
            input.getChannel().position(position);
        }
        return input;
    }

    @Override
    public int read() throws IOException {
        int value = input().read();
        if (value >= 0) {
            position++;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = input().read(buffer, offset, length);
        if (count > 0) {
            position += count;
        }
        return count;
    }

    @Override
    public long skip(long count) throws IOException {
        long skipped = input().skip(Math.max(0, count));
        position += skipped;
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readLimit) {
        marked = position;
    }

    @Override
    public void reset() throws IOException {
        input().getChannel().position(marked);
        position = marked;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (input != null) {
            input.close();
        }
    }
}
