package org.fidonet.binkp.common.io;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Disk-backed receive stream; the byte-array API remains available to event consumers. */
public final class PartialFileOutputStream extends ByteArrayOutputStream {
    private Path path;

    public synchronized Path getPath() {
        return path;
    }

    public synchronized void commitTo(Path destination) throws IOException {
        if (!closed) {
            throw new IOException("Receive stream must be closed before publication");
        }
        if (path.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
            if (!Files.exists(path)) {
                throw new IOException("Completed receive file is missing");
            }
            return;
        }
        if (Files.exists(destination)) {
            verifyExistingFile(destination);
            Files.delete(path);
        } else {
            Files.move(path, destination, StandardCopyOption.ATOMIC_MOVE);
        }
        path = destination;
    }

    private void verifyExistingFile(Path destination) throws IOException {
        try (InputStream first = Files.newInputStream(path);
                InputStream second = Files.newInputStream(destination)) {
            verifyEqualContents(first, second, destination);
        }
    }

    private void verifyEqualContents(InputStream first, InputStream second, Path destination)
            throws IOException {
        while (true) {
            byte[] left = first.readNBytes(8192);
            byte[] right = second.readNBytes(8192);
            if (!Arrays.equals(left, right)) {
                throw new IOException("Different completed file already exists: " + destination);
            }
            if (left.length == 0) {
                return;
            }
        }
    }
    private final FileChannel channel;
    private final FileLock lock;
    private long length;
    private boolean closed;

    public PartialFileOutputStream(Path path, long expectedSize) throws IOException {
        this.path = path;
        channel = FileChannel.open(path,
                                   StandardOpenOption.CREATE,
                                   StandardOpenOption.READ,
                                   StandardOpenOption.WRITE,
                                   LinkOption.NOFOLLOW_LINKS);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
            if (acquired == null) {
                throw new IOException("Partial file is already in use");
            }
            length = channel.size();
            if (length > expectedSize) {
                throw new IOException("Partial file exceeds advertised size");
            }
            channel.position(length);
        } catch (IOException | RuntimeException e) {
            if (acquired != null) {
                acquired.release();
            }
            channel.close();
            throw e;
        }
        lock = acquired;
    }

    public long length() {
        return length;
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int count) {
        try {
            if (closed) {
                throw new IOException("Receive stream is closed");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, count);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            length += count;
            channel.force(false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(int value) {
        write(new byte[]{(byte) value}, 0, 1);
    }

    @Override
    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    @Override
    public synchronized int size() {
        return Math.toIntExact(length);
    }

    @Override
    public synchronized byte[] toByteArray() {
        try {
            if (closed) {
                return Files.readAllBytes(path);
            }
            ByteArrayOutputStream copy = new ByteArrayOutputStream(size());
            readLockedFile(copy);
            return copy.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void writeTo(OutputStream output) throws IOException {
        if (closed) {
            try (InputStream input = Files.newInputStream(path)) {
                input.transferTo(output);
            }
        } else {
            readLockedFile(output);
        }
    }

    private void readLockedFile(OutputStream output) throws IOException {
        // Windows locks also reject reads through a second file handle.
        // Positional reads use our owning channel without changing its append position.
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        long position = 0;
        while (position < length) {
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), length - position));
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new EOFException("Partial file was truncated");
            }
            output.write(buffer.array(), 0, read);
            position += read;
        }
    }

    @Override
    public synchronized String toString() {
        return new String(toByteArray());
    }

    @Override
    public synchronized String toString(String charset) throws UnsupportedEncodingException {
        return new String(toByteArray(), charset);
    }

    @Override
    public synchronized String toString(Charset charset) {
        return new String(toByteArray(), charset);
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("Cannot reset a partial receive file");
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            channel.force(true);
        } finally {
            closed = true;
            releaseResources();
        }
    }

    private void releaseResources() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
