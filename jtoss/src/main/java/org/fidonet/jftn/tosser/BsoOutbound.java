package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.misc.AtomicFiles;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Durable Binkley FLO handoff. External mailers delete copies, never the authoritative queue. */
public final class BsoOutbound {
    private static final Object LOCK = new Object();
    private final Path root;
    private final Path journal;
    private final int zone;

    public BsoOutbound(JFtnConfig config) {
        root = Path.of(config.getValue("BsoOutbound", config.getOutbound()))
                   .toAbsolutePath()
                   .normalize();
        journal = Path.of(config.getTmpDir(), "bso-journal").toAbsolutePath().normalize();
        zone = Integer.parseInt(config.getValue("BsoZone",
                                                config.getLocalAddresses()
                                                      .stream()
                                                      .findFirst()
                                                      .map(address -> address.getZone().toString())
                                                      .orElse("1")));
        if (zone < 1 || zone > 65535) {
            throw new IllegalArgumentException("BsoZone must be 1..65535");
        }
    }

    public static boolean enabled(JFtnConfig config) {
        String value = config.getValue("BsoEnabled", "false");
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("BsoEnabled must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public Path control(FTNAddr address, String extension) {
        if (!extension.matches("[a-z]{3}")) {
            throw new IllegalArgumentException("Invalid BSO extension");
        }
        Path directory = address.getZone() == zone
                ? root
                : root.resolveSibling(root.getFileName()
                                      + String.format(Locale.ROOT, ".%03x", address.getZone()));
        String node = String.format(Locale.ROOT, "%04x%04x", address.getNet(), address.getNode());
        if (address.getPnt() == 0) {
            return directory.resolve(node + "." + extension);
        }
        return directory.resolve(node + ".pnt")
                        .resolve(String.format(Locale.ROOT,
                                               "%08x.%s",
                                               address.getPnt(),
                                               extension));
    }

    public boolean owns(String key, FTNAddr peer) {
        return Files.exists(job(key, peer).resolve("delivery.properties"),
                            LinkOption.NOFOLLOW_LINKS);
    }

    public boolean acknowledged(String key, FTNAddr peer) throws IOException {
        Path job = job(key, peer);
        if (!owns(key, peer)) {
            return false;
        }
        Properties properties = read(job, key, peer);
        if (!"PUBLISHED".equals(properties.getProperty("state"))) {
            return false;
        }
        for (String name : names(properties)) {
            if (Files.exists(job.resolve("files").resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
        }
        return true;
    }

    public void enqueue(String key, Link peer, List<Path> sources, String flavour)
            throws IOException {
        synchronized (LOCK) {
            Path job = job(key, peer.getAddr());
            if (!owns(key, peer.getAddr())) {
                prepare(job, key, peer, sources, flavour);
            }
            Properties properties = read(job, key, peer.getAddr());
            if ("PUBLISHED".equals(properties.getProperty("state"))) {
                return;
            }
            Path flo = control(peer.getAddr(), extension(properties.getProperty("flavour")));
            ensureDirectory(flo.getParent());
            publish(job, peer, flo, properties);
        }
    }

    private void prepare(Path job, String key, Link peer, List<Path> sources, String flavour)
            throws IOException {
        extension(flavour);
        if (sources.isEmpty() || sources.size() > 1024) {
            throw new IOException("BSO handoff requires files");
        }
        ensureDirectory(journal);
        Path staging = Files.createTempDirectory(journal, ".preparing-");
        try {
            Files.createDirectory(staging.resolve("files"));
            Properties properties = new Properties();
            properties.setProperty("key", key);
            properties.setProperty("peer", peer.getAddr().toString());
            properties.setProperty("flavour", flavour);
            properties.setProperty("state", "PREPARED");
            properties.setProperty("count", Integer.toString(sources.size()));
            for (int index = 0; index < sources.size(); index++) {
                Path source = sources.get(index);
                String name = safeName(source.getFileName().toString());
                properties.setProperty("file." + index, name);
                copySource(source, staging.resolve("files").resolve(name));
            }
            write(staging, properties);
            Files.move(staging, job, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            removeStaging(staging);
        }
    }

    private static void copySource(Path source, Path copy) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("BSO source must be a regular file");
        }
        AtomicFiles.publish(copy, output -> transfer(source, output));
        if (Files.size(copy) != Files.size(source)) {
            throw new IOException("BSO source changed during handoff");
        }
    }

    private static void transfer(Path source, OutputStream output) throws IOException {
        try (var input = Files.newInputStream(source)) {
            input.transferTo(output);
        }
    }

    private void publish(Path job, Link peer, Path flo, Properties properties) throws IOException {
        Path busy = control(peer.getAddr(), "bsy");
        if (Files.exists(control(peer.getAddr(), "csy"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("BSO peer is calling/busy");
        }
        boolean acquired = false;
        try {
            try (FileChannel channel = FileChannel.open(busy,
                                                        StandardOpenOption.CREATE_NEW,
                                                        StandardOpenOption.WRITE)) {
                acquired = true;
                appendFlo(job, flo, properties);
                properties.setProperty("state", "PUBLISHED");
                write(job, properties);
            }
        } finally {
            if (acquired) {
                Files.delete(busy);
            }
        }
    }

    private void appendFlo(Path job, Path flo, Properties properties) throws IOException {
        if (Files.isSymbolicLink(flo) || Files.exists(flo) && Files.size(flo) > 16 * 1024 * 1024) {
            throw new IOException("Unsafe or oversized BSO control file");
        }
        List<String> lines = Files.exists(flo)
                ? Files.readAllLines(flo, StandardCharsets.UTF_8)
                : new ArrayList<>();
        for (String name : names(properties)) {
            String line = "^" + job.resolve("files").resolve(name);
            if (line.chars().anyMatch(character -> character < 32 || character == 127)) {
                throw new IOException("Unsafe BSO file path");
            }
            if (!lines.contains(line)) {
                lines.add(line);
            }
        }
        AtomicFiles.replace(flo,
                            output -> output.write((String.join("\r\n", lines)
                                    + "\r\n").getBytes(StandardCharsets.UTF_8)));
    }

    private Properties read(Path job, String key, FTNAddr peer) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(job.resolve("delivery.properties"))) {
            properties.load(input);
        }
        if (!key.equals(properties.getProperty("key"))
                || !peer.toString().equals(properties.getProperty("peer"))) {
            throw new IOException("BSO handoff identity conflict");
        }
        return properties;
    }

    private List<String> names(Properties properties) throws IOException {
        int count;
        try {
            count = Integer.parseInt(properties.getProperty("count", "0"));
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid BSO journal", failure);
        }
        if (count < 1 || count > 1024) {
            throw new IOException("Invalid BSO file count");
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(safeName(properties.getProperty("file." + index)));
        }
        return result;
    }

    private static String safeName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.matches(".*[\\x00-\\x1f\\x7f/\\\\:].*")) {
            throw new IOException("Unsafe BSO payload filename");
        }
        return name;
    }

    private static String extension(String flavour) {
        switch (flavour) {
            case "normal" :
                return "flo";
            case "crash" :
                return "clo";
            case "hold" :
                return "hlo";
            case "direct" :
                return "dlo";
            case "immediate" :
                return "ilo";
            default :
                throw new IllegalArgumentException("Invalid BSO flavour");
        }
    }

    private Path job(String key, FTNAddr peer) {
        return journal.resolve(UUID.nameUUIDFromBytes((peer + ":"
                + key).getBytes(StandardCharsets.UTF_8)).toString());
    }

    private static void write(Path job, Properties properties) throws IOException {
        AtomicFiles.replace(job.resolve("delivery.properties"),
                            output -> properties.store(output, "JFtn BSO delivery"));
    }

    private static void ensureDirectory(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("BSO directories cannot contain symbolic links");
            }
        }
        Files.createDirectories(path);
    }

    private static void removeStaging(Path staging) throws IOException {
        if (!Files.isDirectory(staging)) {
            return;
        }
        for (Path file : stagingFiles(staging.resolve("files"))) {
            Files.delete(file);
        }
        Files.deleteIfExists(staging.resolve("files"));
        Files.deleteIfExists(staging.resolve("delivery.properties"));
        Files.delete(staging);
    }

    private static List<Path> stagingFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.collect(Collectors.toList());
        }
    }
}
