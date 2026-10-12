package org.fidonet.binkp.common.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;

/** Disk-backed receive stream; the byte-array API remains available to event consumers. */
public final class PartialFileOutputStream extends ByteArrayOutputStream {
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;
    private long length;
    private boolean closed;

    public PartialFileOutputStream(Path path, long expectedSize) throws IOException {
        this.path = path;
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
            if (acquired == null) throw new IOException("Partial file is already in use");
            length = channel.size();
            if (length > expectedSize) throw new IOException("Partial file exceeds advertised size");
            channel.position(length);
        } catch (IOException | RuntimeException e) {
            if (acquired != null) acquired.release();
            channel.close(); throw e;
        }
        lock = acquired;
    }

    public long length() { return length; }
    @Override public synchronized void write(byte[] bytes, int offset, int count) {
        try {
            if (closed) throw new IOException("Receive stream is closed");
            ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, count);
            while (buffer.hasRemaining()) channel.write(buffer);
            length += count;
            channel.force(false);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public void write(int value) { write(new byte[]{(byte)value}, 0, 1); }
    @Override public void write(byte[] bytes) { write(bytes, 0, bytes.length); }
    @Override public synchronized int size() { return Math.toIntExact(length); }
    @Override public synchronized byte[] toByteArray() {
        try { return Files.readAllBytes(path); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public synchronized void writeTo(java.io.OutputStream output) throws IOException {
        try (java.io.InputStream input = Files.newInputStream(path)) { input.transferTo(output); }
    }
    @Override public synchronized String toString() { return new String(toByteArray()); }
    @Override public synchronized String toString(String charset) throws java.io.UnsupportedEncodingException {
        return new String(toByteArray(), charset);
    }
    @Override public synchronized String toString(java.nio.charset.Charset charset) { return new String(toByteArray(), charset); }
    @Override public void reset() { throw new UnsupportedOperationException("Cannot reset a partial receive file"); }
    @Override public synchronized void close() throws IOException {
        if (!closed) {
            try { channel.force(true); }
            finally {
                closed = true;
                try { lock.release(); } finally { channel.close(); }
            }
        }
    }
}
