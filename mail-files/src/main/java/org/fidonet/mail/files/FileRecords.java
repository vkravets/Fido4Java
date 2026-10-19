package org.fidonet.mail.files;

import org.fidonet.misc.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Atomic records with opaque, traversal-safe names. Runtime ownership is locked by MailRuntime. */
final class FileRecords {
    private FileRecords() {
    }

    static String key(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                                         .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static Properties read(Path path) {
        Properties result = new Properties();
        if (!Files.exists(path)) {
            return result;
        }
        try (InputStream input = Files.newInputStream(path)) {
            result.load(input);
            if (!"1".equals(result.getProperty("format"))) {
                throw new IOException("Unsupported mail record format: " + path);
            }
            return result;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static void write(Path path, Properties record) {
        record.setProperty("format", "1");
        try {
            Files.createDirectories(path.getParent());
            AtomicFiles.replace(path, output -> record.store(output, "JFtn mail state"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static List<Path> list(Path directory) {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".properties"))
                        .sorted()
                        .collect(Collectors.toList());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
