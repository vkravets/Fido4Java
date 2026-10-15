package org.fidonet.misc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Publishes fully written and forced files; callers choose the replacement policy. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void publish(Path target, Writer writer) throws IOException {
        write(target, writer, false);
    }

    public static void replace(Path target, Writer writer) throws IOException {
        write(target, writer, true);
    }

    private static void write(Path target, Writer writer, boolean replace) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "jftn-write-", ".tmp");
        try {
            writeContents(temporary, writer);
            move(temporary, target, replace);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeContents(Path temporary, Writer writer) throws IOException {
        try (OutputStream output = Files.newOutputStream(temporary)) {
            writer.write(output);
        }
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        if (replace) {
            Files.move(source,
                       target,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    public interface Writer {
        void write(OutputStream output) throws IOException;
    }
}
