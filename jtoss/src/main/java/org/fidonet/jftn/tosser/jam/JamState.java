package org.fidonet.jftn.tosser.jam;

import org.fidonet.misc.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        AtomicFiles.replace(path, output -> values.store(output, "JFtn JAM bridge receipts"));
    }
}
