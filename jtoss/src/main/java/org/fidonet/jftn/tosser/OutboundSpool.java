package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

/** Persistent packets and acknowledgement receipts; preparing a packet does not mark it sent. */
public final class OutboundSpool {
    private final Path root;
    private final JFtnConfig config;

    public OutboundSpool(JFtnConfig config) {
        this.config = config;
        root = Path.of(config.getOutbound(), "jftn-mail");
    }

    public boolean isAcknowledged(String key, Link link) {
        String name = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        return Files.exists(directory(link.getAddr()).resolve(name + ".sent"));
    }

    public synchronized void enqueue(String key, Link link, Message message) throws IOException {
        Path directory = directory(link.getAddr());
        Files.createDirectories(directory);
        String name = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        Path packet = directory.resolve(name + ".pkt");
        Path metadata = directory.resolve(name + ".properties");
        if (Files.exists(directory.resolve(name + ".sent"))) {
            return;
        }
        if (!Files.exists(packet)) {
            if (link.getMyaddr() == null || link.getMyaddr().isEmpty()) {
                throw new IOException("Missing local AKA for " + link.getAddr());
            }
            byte[] bytes = PacketWriter.write(link.getMyaddr().get(0),
                                              link.getAddr(),
                                              config.getPacketPassword(link),
                                              Collections.singletonList(message));
            writeAtomically(packet, bytes);
        }
        if (!Files.exists(metadata)) {
            Properties properties = new Properties();
            properties.setProperty("key", key);
            properties.setProperty("hold",
                                   Boolean.toString((message.getAttributes() & 512) != 0
                                           || (" " + NetmailRouter.configFlags(message)
                                                   + " ").contains(" HLD ")));
            Path staging = Files.createTempFile(directory, "metadata-", ".tmp");
            try {
                try (OutputStream out = Files.newOutputStream(staging)) {
                    properties.store(out, "JFtn outbound");
                }
                force(staging);
                Files.move(staging, metadata, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(staging);
            }
        }
    }

    public synchronized List<Path> pending(Link link, boolean includeHeld) throws IOException {
        Path directory = directory(link.getAddr());
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return result;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path metadata : (Iterable<Path>) files.filter(path -> path.toString()
                                                                           .endsWith(".properties"))::iterator) {
                Properties properties = properties(metadata);
                Path packet = Path.of(metadata.toString().replaceFirst("\\.properties$", ".pkt"));
                if (Files.exists(packet) && !Files.exists(receipt(packet))
                        && (includeHeld || !Boolean.parseBoolean(properties.getProperty("hold")))) {
                    result.add(packet);
                }
            }
        }
        return result;
    }

    public synchronized String acknowledge(Path packet) throws IOException {
        Path expected = packet.toAbsolutePath().normalize();
        if (!expected.startsWith(root.toAbsolutePath().normalize())) {
            throw new IOException("Packet is outside outbound spool");
        }
        Properties properties = properties(Path.of(packet.toString()
                                                         .replaceFirst("\\.pkt$", ".properties")));
        writeAtomically(receipt(packet), new byte[]{1});
        Files.deleteIfExists(packet);
        return properties.getProperty("key");
    }

    private Path directory(FTNAddr address) {
        return root.resolve(address.getZone()
                            + "-"
                            + address.getNet()
                            + "-"
                            + address.getNode()
                            + "-"
                            + address.getPnt());
    }

    private static Path receipt(Path packet) {
        return Path.of(packet.toString().replaceFirst("\\.pkt$", ".sent"));
    }

    private static Properties properties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path staging = Files.createTempFile(target.getParent(), "packet-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }
}
