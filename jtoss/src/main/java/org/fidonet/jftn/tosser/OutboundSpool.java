package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.misc.AtomicFiles;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Persistent packets and acknowledgement receipts; preparing a packet does not mark it sent. */
public final class OutboundSpool {
    private static final Object PUBLICATION_LOCK = new Object();
    private final Path root;
    private final JFtnConfig config;

    public OutboundSpool(JFtnConfig config) {
        this.config = config;
        root = Path.of(config.getOutbound(), "jftn-mail");
    }

    public boolean isAcknowledged(String key, Link link) throws IOException {
        return isAcknowledged(key, link.getAddr());
    }

    public boolean isAcknowledged(String key, FTNAddr address) throws IOException {
        String name = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        if (Files.exists(directory(address).resolve(name + ".sent"))) {
            return true;
        }
        BsoOutbound bso = new BsoOutbound(config);
        if (bso.acknowledged("packet:" + key, address)) {
            Path metadata = directory(address).resolve(name + ".properties");
            acknowledge(packet(metadata, properties(metadata)));
            return true;
        }
        return false;
    }

    public synchronized void enqueue(String key, Link link, Message message) throws IOException {
        FTNAddr local = link.getMyaddr() == null || link.getMyaddr().isEmpty()
                ? null
                : link.getMyaddr().get(0);
        enqueue(key, link, message, local);
    }

    public synchronized void enqueue(String key, Link link, Message message, FTNAddr local)
            throws IOException {
        synchronized (PUBLICATION_LOCK) {
            enqueuePacket(key, link, message, local);
        }
    }

    private void enqueuePacket(String key, Link link, Message message, FTNAddr local)
            throws IOException {
        Path directory = directory(link.getAddr());
        Files.createDirectories(directory);
        String name = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        Path metadata = directory.resolve(name + ".properties");
        if (Files.exists(directory.resolve(name + ".sent"))) {
            return;
        }
        if (local == null || link.getMyaddr() == null || !link.getMyaddr().contains(local)) {
            throw new IOException("Missing local AKA for " + link.getAddr());
        }
        if (!Files.exists(metadata)) {
            Properties properties = new Properties();
            properties.setProperty("key", key);
            // Recover a legacy packet whose publication stopped before writing its metadata.
            if (!Files.exists(directory.resolve(name + ".pkt"))) {
                properties.setProperty("packet", PacketNames.allocate(root, directory));
            }
            properties.setProperty("hold",
                                   Boolean.toString(NetmailPriority.of(message) == NetmailPriority.HOLD));
            AtomicFiles.publish(metadata, out -> properties.store(out, "JFtn outbound"));
        }
        Properties properties = properties(metadata);
        if (!key.equals(properties.getProperty("key"))) {
            throw new IOException("Outbound delivery identity conflict");
        }
        Path packet = packet(metadata, properties);
        if (properties.containsKey("packet")) {
            Path owner = directory.resolve(packet.getFileName() + ".owner");
            if (!Files.exists(owner)) {
                if (Files.exists(packet)) {
                    throw new IOException("Outbound packet has no owner; refusing to reuse it");
                }
                writeAtomically(owner, name.getBytes(StandardCharsets.US_ASCII));
            }
            if (!name.equals(Files.readString(owner, StandardCharsets.US_ASCII))) {
                throw new IOException("Outbound packet name conflict");
            }
        }
        if (!Files.exists(packet)) {
            byte[] bytes = PacketWriter.write(local,
                                              link.getAddr(),
                                              config.getPacketPassword(link),
                                              Collections.singletonList(message));
            writeAtomically(packet, bytes);
        }
        BsoOutbound bso = new BsoOutbound(config);
        if (BsoOutbound.enabled(config)) {
            List<BsoOutbound.File> sources = BsoAttachments.sources(config, message);
            sources.add(new BsoOutbound.File(packet, packet.getFileName().toString()));
            bso.enqueueNamed("packet:" + key,
                             link,
                             sources,
                             NetmailPriority.of(message).name().toLowerCase(Locale.ROOT));
        } else if (bso.owns("packet:" + key, link.getAddr())) {
            throw new IOException("Delivery remains owned by external BSO; drain it before changing modes");
        }
    }

    public List<FTNAddr> peers() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .map(this::peerAddress)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        }
    }

    private FTNAddr peerAddress(Path path) {
        String name = path.getFileName().toString();
        if (!name.matches("[0-9]{1,5}(?:-[0-9]{1,5}){3}")) {
            return null;
        }
        String[] parts = name.split("-");
        FTNAddr address = new FTNAddr(parts[0] + ":" + parts[1] + "/" + parts[2] + "." + parts[3]);
        return address.isValid() ? address : null;
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
                String key = properties.getProperty("key", "");
                if (BsoOutbound.enabled(config)
                        || new BsoOutbound(config).owns("packet:" + key, link.getAddr())) {
                    continue;
                }
                if (!key.startsWith("netmail:")
                        && new RobotDelivery(config).isPaused("AreaFix", link.getAddr())) {
                    continue;
                }
                Path packet = packet(metadata, properties);
                if (Files.exists(packet) && !Files.exists(receipt(metadata))
                        && (includeHeld || !Boolean.parseBoolean(properties.getProperty("hold")))) {
                    if (properties.containsKey("packet") && !metadata(packet).equals(metadata)) {
                        throw new IOException("Outbound packet owner mismatch");
                    }
                    result.add(packet);
                }
            }
        }
        return result;
    }

    public synchronized String acknowledge(Path packet) throws IOException {
        synchronized (PUBLICATION_LOCK) {
            return acknowledgePacket(packet);
        }
    }

    private String acknowledgePacket(Path packet) throws IOException {
        Path expected = packet.toAbsolutePath().normalize();
        if (!expected.startsWith(root.toAbsolutePath().normalize())) {
            throw new IOException("Packet is outside outbound spool");
        }
        Path metadata = metadata(packet);
        Properties properties = properties(metadata);
        String key = properties.getProperty("key");
        if (key == null
                || !metadata.getFileName()
                            .toString()
                            .equals(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
                                    + ".properties")) {
            throw new IOException("Outbound delivery identity mismatch");
        }
        if (!packet(metadata, properties).toAbsolutePath().normalize().equals(expected)) {
            throw new IOException("Outbound packet metadata mismatch");
        }
        Path receipt = receipt(metadata);
        if (!Files.exists(receipt)) {
            writeAtomically(receipt, new byte[]{1});
        }
        Files.deleteIfExists(packet);
        return key;
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

    private static Path receipt(Path metadata) {
        return Path.of(metadata.toString().replaceFirst("\\.properties$", ".sent"));
    }

    private static Path packet(Path metadata, Properties properties) throws IOException {
        String name = properties.getProperty("packet");
        if (name == null) {
            return Path.of(metadata.toString().replaceFirst("\\.properties$", ".pkt"));
        }
        if (!name.matches("[0-9a-f]{8}\\.pkt")) {
            throw new IOException("Invalid outbound packet name");
        }
        return metadata.resolveSibling(name);
    }

    private static Path metadata(Path packet) throws IOException {
        Path owner = packet.resolveSibling(packet.getFileName() + ".owner");
        if (!Files.exists(owner)) {
            return Path.of(packet.toString().replaceFirst("\\.pkt$", ".properties"));
        }
        String name = Files.readString(owner, StandardCharsets.US_ASCII);
        if (!name.matches("[0-9a-f-]{36}")) {
            throw new IOException("Invalid outbound packet owner");
        }
        return packet.resolveSibling(name + ".properties");
    }

    private static Properties properties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        AtomicFiles.publish(target, output -> output.write(bytes));
    }
}
