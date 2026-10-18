package org.fidonet.jftn.tosser;

import org.fidonet.misc.MailBundleUnpacker;
import org.fidonet.types.FTNAddr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Packet recovery files and bounded snapshots. The owning tosser supplies synchronization. */
public final class PacketQuarantine {
    private static final Logger logger = LoggerFactory.getLogger(PacketQuarantine.class);
    public static final class Retry {
        public final boolean accepted;
        public final String reason;

        Retry(boolean accepted, String reason) {
            this.accepted = accepted;
            this.reason = reason;
        }
    }
    public static final class Item {
        public final String id;
        public final String name;
        public final long bytes;
        public final long modified;
        public final String checksum;
        public final String peer;
        public final String reason;
        public final int attempts;
        public final long lastAttempt;

        private Item(String id,
                     String name,
                     long bytes,
                     long modified,
                     String checksum,
                     Properties metadata) {
            this.id = id;
            this.name = name;
            this.bytes = bytes;
            this.modified = modified;
            this.checksum = checksum;
            peer = metadata.getProperty("peer", "");
            reason = metadata.getProperty("reason", "No recorded reason (legacy packet).");
            attempts = Integer.parseInt(metadata.getProperty("attempts", "0"));
            lastAttempt = Long.parseLong(metadata.getProperty("lastAttempt", "0"));
        }
    }

    public static final class Page {
        public final List<Item> items;
        public final String nextCursor;
        public final boolean hasMore;

        private Page(List<Item> items, String nextCursor, boolean hasMore) {
            this.items = List.copyOf(items);
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }
    }

    static final class Snapshot {
        final Path path;
        final Item item;
        final byte[] bytes;

        Snapshot(Path path, Item item, byte[] bytes) {
            this.path = path;
            this.item = item;
            this.bytes = bytes;
        }
    }

    private final Path configuredRoot;

    PacketQuarantine(Path root) {
        configuredRoot = root.toAbsolutePath().normalize();
    }

    private Path root() throws IOException {
        return configuredRoot.toRealPath();
    }

    private String relative(Path path) throws IOException {
        Path root = root();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)
                || !normalized.getParent().toRealPath().startsWith(root)
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("Quarantine path escapes the configured temporary directory");
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private String id(Path path) throws IOException {
        return hash(relative(path).getBytes(StandardCharsets.UTF_8));
    }

    private Path internal(String directory) throws IOException {
        Path base = root().resolve(".quarantine");
        Files.createDirectories(base);
        if (!base.toRealPath().startsWith(root())) {
            throw new IOException("Quarantine recovery directory escapes the temporary directory");
        }
        Path path = base.resolve(directory);
        Files.createDirectories(path);
        if (!path.toRealPath().startsWith(root())) {
            throw new IOException("Quarantine recovery directory escapes the temporary directory");
        }
        return path;
    }

    private List<Path> packets() throws IOException {
        Path root = root();
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory,
                                                     BasicFileAttributes attributes) {
                return directory.equals(root.resolve(".quarantine"))
                        || directory.equals(root.resolve("toss-journal"))
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && path.getFileName()
                                                      .toString()
                                                      .toLowerCase(Locale.ROOT)
                                                      .endsWith(".bad")) {
                    result.add(path);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.comparing(path -> root.relativize(path).toString()));
        return result;
    }

    Page list(String after, int limit) throws IOException {
        if (limit < 1 || limit > 200 || after == null || !after.isEmpty() && !validHash(after)) {
            throw new IllegalArgumentException("Invalid quarantine page request");
        }
        List<Path> paths = packets();
        int start = cursor(paths, after);
        List<Item> items = new ArrayList<>();
        int end = Math.min(paths.size(), start + limit);
        for (int index = start; index < end; index++) {
            items.add(describe(paths.get(index)));
        }
        return new Page(items,
                        items.isEmpty() ? "" : items.get(items.size() - 1).id,
                        end < paths.size());
    }

    private int cursor(List<Path> paths, String after) throws IOException {
        if (after.isEmpty()) {
            return 0;
        }
        for (int index = 0; index < paths.size(); index++) {
            if (id(paths.get(index)).equals(after)) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("Quarantine changed. Refresh the packet list.");
    }

    private Item describe(Path path) throws IOException {
        String id = id(path);
        long size = Files.size(path);
        Properties metadata = new Properties();
        String checksum = "";
        if (size <= MailBundleUnpacker.MAX_PACKET_BYTES) {
            try {
                checksum = checksum(path);
                metadata = metadata(id, checksum);
            } catch (IOException | IllegalArgumentException failure) {
                logger.warn("Unable to inspect quarantined packet {}", path, failure);
                checksum = "";
                metadata = new Properties();
                metadata.setProperty("reason",
                                     "Packet metadata is unavailable; inspect the node log.");
            }
        } else {
            metadata.setProperty("reason", "Packet exceeds the processing size limit.");
        }
        return new Item(id,
                        relative(path),
                        size,
                        Files.getLastModifiedTime(path).toMillis(),
                        checksum,
                        metadata);
    }

    private Properties metadata(String id, String checksum) throws IOException {
        Path path = internal("metadata").resolve(id + ".properties");
        Properties values = new Properties();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return values;
        }
        if (Files.size(path) > 131072) {
            throw new IOException("Quarantine metadata exceeds size limit");
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(path,
                                                                        LinkOption.NOFOLLOW_LINKS),
                                                   StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        if (!checksum.equals(values.getProperty("checksum"))) {
            throw new IOException("Quarantine metadata does not match the packet");
        }
        String peer = values.getProperty("peer", "");
        if (!peer.isEmpty()) {
            if (!new FTNAddr(peer).toString().equals(peer)) {
                throw new IOException("Invalid quarantine session peer metadata");
            }
        }
        if (Integer.parseInt(values.getProperty("attempts", "0")) < 0
                || Long.parseLong(values.getProperty("lastAttempt", "0")) < 0) {
            throw new IOException("Invalid quarantine attempt metadata");
        }
        return values;
    }

    void record(Path path, byte[] bytes, FTNAddr peer, String reason) throws IOException {
        String checksum = hash(bytes);
        Properties values;
        try {
            values = metadata(id(path), checksum);
        } catch (IOException | IllegalArgumentException failure) {
            // A new packet can legitimately reuse an old completed packet's filename.
            values = new Properties();
        }
        String recordedPeer = values.getProperty("peer", "");
        if (!recordedPeer.isEmpty() && peer != null && !recordedPeer.equals(peer.toString())) {
            return;
        }
        values.setProperty("checksum", checksum);
        values.setProperty("peer",
                           recordedPeer.isEmpty() && peer != null ? peer.toString() : recordedPeer);
        values.setProperty("reason", reason);
        writeMetadata(id(path), values);
    }

    Snapshot snapshot(String id, String expectedChecksum) throws IOException {
        if (!validHash(id) || !validHash(expectedChecksum)) {
            throw new IllegalArgumentException("Invalid quarantine packet identifier or checksum");
        }
        Path path = find(id);
        Item item = describe(path);
        if (!expectedChecksum.equals(item.checksum)) {
            throw new IllegalArgumentException("Packet changed. Refresh the quarantine list.");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MailBundleUnpacker.MAX_PACKET_BYTES + 1);
        }
        if (bytes.length > MailBundleUnpacker.MAX_PACKET_BYTES
                || !hash(bytes).equals(expectedChecksum)) {
            throw new IllegalArgumentException("Packet changed. Refresh the quarantine list.");
        }
        return new Snapshot(path, item, bytes);
    }

    private Path find(String id) throws IOException {
        for (Path path : packets()) {
            if (id(path).equals(id)) {
                return path;
            }
        }
        throw new IllegalArgumentException("Quarantined packet was not found. Refresh the list.");
    }

    void attempted(Snapshot snapshot) throws IOException {
        Properties values = metadata(snapshot.item.id, snapshot.item.checksum);
        long attempts = Math.min(Integer.MAX_VALUE, (long) snapshot.item.attempts + 1);
        values.setProperty("attempts", Long.toString(attempts));
        values.setProperty("lastAttempt", Long.toString(System.currentTimeMillis()));
        update(snapshot, values, "Retry started; if interrupted, retry again safely.");
    }

    void rejected(Snapshot snapshot, String reason) throws IOException {
        update(snapshot, metadata(snapshot.item.id, snapshot.item.checksum), reason);
    }

    private void update(Snapshot snapshot, Properties values, String reason) throws IOException {
        values.setProperty("checksum", snapshot.item.checksum);
        values.setProperty("peer", snapshot.item.peer);
        values.setProperty("reason", reason);
        writeMetadata(snapshot.item.id, values);
    }

    void preserve(Snapshot snapshot) throws IOException {
        Path destination = internal("archive").resolve(snapshot.item.checksum + ".bad");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            if (!checksum(destination).equals(snapshot.item.checksum)) {
                throw new IOException("Quarantine recovery archive checksum mismatch");
            }
            return;
        }
        Path temporary = Files.createTempFile(destination.getParent(), "packet-", ".part");
        try {
            writePacket(temporary, snapshot.bytes);
            Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void complete(Snapshot snapshot) throws IOException {
        Path archive = internal("archive").resolve(snapshot.item.checksum + ".bad");
        if (!checksum(archive).equals(snapshot.item.checksum)) {
            throw new IOException("Quarantine recovery archive checksum mismatch");
        }
        relative(snapshot.path);
        if (!checksum(snapshot.path).equals(snapshot.item.checksum)) {
            throw new IllegalArgumentException("Packet changed. Refresh the quarantine list.");
        }
        Files.delete(snapshot.path);
    }

    private void writeMetadata(String id, Properties values) throws IOException {
        Path destination = internal("metadata").resolve(id + ".properties");
        Path temporary = Files.createTempFile(destination.getParent(), "metadata-", ".part");
        try {
            storeProperties(temporary, values);
            move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void storeProperties(Path path, Properties values) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            values.store(writer, "JFtn quarantine metadata; contains no packet password");
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void writePacket(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source,
                       destination,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean validHash(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static String checksum(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] block = new byte[65536];
            int count;
            long total = 0;
            while ((count = input.read(block)) != -1) {
                total += count;
                if (total > MailBundleUnpacker.MAX_PACKET_BYTES) {
                    throw new IOException("Quarantine packet exceeds size limit");
                }
                digest.update(block, 0, count);
            }
        }
        return hex(digest.digest());
    }

    private static String hash(byte[] bytes) {
        return hex(digest().digest(bytes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }
}
