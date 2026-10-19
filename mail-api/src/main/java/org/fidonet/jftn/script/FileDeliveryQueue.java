package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.RobotDelivery;
import org.fidonet.types.Link;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Immutable file/TIC pairs retained until the transport acknowledges each file. */
public final class FileDeliveryQueue {
    public static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private final Path root;
    private final RobotDelivery delivery;

    public FileDeliveryQueue(JFtnConfig config) {
        root = Path.of(config.getOutbound(), "file-delivery").toAbsolutePath().normalize();
        delivery = new RobotDelivery(config);
    }

    public synchronized void enqueue(Link peer, String key, Path source, String name, String tic)
            throws IOException {
        enqueue(peer, key, source, name, tic.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized void enqueue(Link peer, String key, Path source, String name, byte[] tic)
            throws IOException {
        ReceivedFile.safeName(name);
        if (tic.length > 65536) {
            throw new IOException("Outgoing TIC exceeds 64 KiB");
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".tic")) {
            throw new IllegalArgumentException("A TIC cannot be its own payload");
        }
        Path parent = root.resolve(ReceivedFile.directoryName(peer.getAddr()));
        String id = ScriptState.digest(key);
        Path destination = parent.resolve(id);
        if (Files.exists(destination)) {
            return;
        }
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, ".staging-");
        try {
            copy(source, staging.resolve(name));
            Files.write(staging.resolve(id.substring(0, 8) + ".tic"), tic);
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            deleteStaging(staging);
        }
    }

    private static void deleteStaging(Path staging) throws IOException {
        if (!Files.exists(staging)) {
            return;
        }
        try (Stream<Path> files = Files.list(staging)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.delete(file);
            }
        }
        Files.delete(staging);
    }

    public static void copy(Path source, Path destination) throws IOException {
        if (Files.size(source) > MAX_FILE_BYTES) {
            throw new IOException("Fileecho payload exceeds 128 MiB");
        }
        try (InputStream input = Files.newInputStream(source);
                OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) {
                    throw new IOException("Fileecho payload grew beyond 128 MiB");
                }
                output.write(buffer, 0, count);
            }
        }
    }

    /** One pair per session prevents different jobs with identical filenames from colliding. */
    public synchronized List<Path> pending(Link peer) throws IOException {
        if (delivery.isPaused("FileFix", peer.getAddr())) {
            return List.of();
        }
        Path directory = root.resolve(ReceivedFile.directoryName(peer.getAddr()));
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> jobs;
        try (Stream<Path> paths = Files.list(directory)) {
            jobs = paths.filter(Files::isDirectory)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .sorted()
                        .collect(Collectors.toList());
        }
        for (Path job : jobs) {
            List<Path> result = pendingJob(job);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return List.of();
    }

    private List<Path> pendingJob(Path job) throws IOException {
        List<Path> result;
        try (Stream<Path> paths = Files.list(job)) {
            result = paths.filter(this::isPendingFile).collect(Collectors.toList());
        }
        result.sort(Comparator.comparing(path -> path.getFileName()
                                                     .toString()
                                                     .toLowerCase(Locale.ROOT)
                                                     .endsWith(".tic")));
        return result;
    }

    private boolean isPendingFile(Path path) {
        return Files.isRegularFile(path) && !path.getFileName().toString().startsWith(".")
                && !Files.exists(marker(path));
    }

    public boolean owns(Path path) {
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    public synchronized void acknowledge(Path path) throws IOException {
        if (!owns(path)) {
            throw new IOException("File does not belong to this queue");
        }
        new ScriptState(path.getParent().resolve(".receipts")).put("sent:" + path.getFileName(),
                                                                   "sent");
        Files.deleteIfExists(path);
    }

    private Path marker(Path path) {
        return path.getParent()
                   .resolve(".receipts")
                   .resolve(ScriptState.digest("sent:" + path.getFileName()));
    }
}
