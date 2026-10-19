package org.fidonet.mail.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Exclusive ownership covers catalog reads, queue recovery and the complete store lifetime. */
final class FileStateLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    FileStateLock(Path directory) {
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(directory.resolve("store.lock"),
                                       StandardOpenOption.CREATE,
                                       StandardOpenOption.WRITE);
            lock = acquire(channel);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static FileLock acquire(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("MailStatePath is already in use");
            }
            return lock;
        } catch (IOException | OverlappingFileLockException failure) {
            channel.close();
            throw new IOException("MailStatePath is already in use", failure);
        }
    }

    @Override
    public void close() {
        if (!channel.isOpen()) {
            return;
        }
        try {
            lock.release();
            channel.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
