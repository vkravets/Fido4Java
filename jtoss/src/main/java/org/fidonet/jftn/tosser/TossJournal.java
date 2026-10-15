package org.fidonet.jftn.tosser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Durable progress for a packet retained after a partial import. */
final class TossJournal {
    private final Path directory;

    TossJournal(Path root, ByteBuffer packet) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer copy = packet.duplicate();
            copy.position(0);
            digest.update(copy);
            StringBuilder name = new StringBuilder();
            for (byte value : digest.digest()) {
                name.append(String.format("%02x", value & 255));
            }
            directory = root.resolve("toss-journal").resolve(name.toString());
            Files.createDirectories(directory);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    boolean contains(int index) {
        return Files.exists(directory.resolve(index + ".done"));
    }

    String key(int index) {
        return directory.getFileName() + ":" + index;
    }

    void complete(int index) throws IOException {
        try (FileChannel channel = FileChannel.open(directory.resolve(index + ".done"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }
}
