package org.fidonet.jftn.tosser.jam;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Durable bridge receipts. Updates replace one complete journal atomically. */
final class JamState {
    private final Path path;
    private final Properties values = new Properties();

    JamState(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                values.load(input);
            }
        }
    }

    String get(String key) {
        return values.getProperty(key);
    }

    void put(String key, String value) throws IOException {
        values.setProperty(key, value);
        save();
    }

    void prepare(String identity, String fingerprint, String targets) throws IOException {
        values.setProperty("import." + identity, fingerprint);
        values.setProperty("targets." + identity, targets);
        values.setProperty("stage." + identity, "PREPARED");
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "jam-state-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "JFtn JAM bridge receipts");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary,
                       path,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
