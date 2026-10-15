package org.fidonet.jftn.script;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Small durable values owned by a script; mail processing serializes their use. */
public final class ScriptState {
    private final Path root;

    public ScriptState(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
    }

    public static String digest(String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                                        .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String get(String key) throws IOException {
        Path path = root.resolve(digest(key));
        return Files.exists(path) ? Files.readString(path) : null;
    }

    public void put(String key, String value) throws IOException {
        Path temporary = Files.createTempFile(root, ".value-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer data = StandardCharsets.UTF_8.encode(value);
                while (data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true);
            }
            Files.move(temporary,
                       root.resolve(digest(key)),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
