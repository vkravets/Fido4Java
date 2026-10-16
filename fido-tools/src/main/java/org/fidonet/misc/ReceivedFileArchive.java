package org.fidonet.misc;

import org.fidonet.config.JFtnConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stores original bytes before processing can remove them, using content-addressed directories. */
public final class ReceivedFileArchive {
    private ReceivedFileArchive() {
    }

    public static void preserve(JFtnConfig config, Path source, String originalName)
            throws IOException {
        if (!config.isPreserveReceivedFiles()) {
            return;
        }
        preserve(config.getReceivedArchivePath(), source, originalName);
    }

    public static void preserve(Path archiveDirectory, Path source, String originalName)
            throws IOException {
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.contains(":")) {
            throw new IOException("Invalid received archive filename");
        }
        Path root = archiveDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path stage = Files.createTempFile(root, ".received-", ".tmp");
        try {
            String digest = copy(source, stage);
            Path directory = root.resolve(digest);
            Files.createDirectories(directory);
            Path destination = directory.resolve(name);
            if (!Files.exists(destination)) {
                publish(stage, destination);
            }
        } finally {
            Files.deleteIfExists(stage);
        }
    }

    private static void publish(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException alreadyArchived) {
            // Concurrent processing preserved the same content and name.
        }
    }

    private static String copy(Path source, Path destination) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(source);
                OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        try (FileChannel channel = FileChannel.open(destination, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
