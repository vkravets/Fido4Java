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
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Locale;
import java.util.Properties;

/** Retains duplicate messages outside normal mailboxes and the outbound queue. */
final class DuplicateMailArchive {
    private final Path root;
    private final boolean discard;

    DuplicateMailArchive(JFtnConfig config) {
        String action = config.getValue("DupeAction", "move");
        if (!"move".equalsIgnoreCase(action) && !"discard".equalsIgnoreCase(action)) {
            throw new IllegalArgumentException("DupeAction must be move or discard");
        }
        discard = "discard".equalsIgnoreCase(action);
        root = Path.of(config.getValue("DupePath",
                                       Path.of(config.getTmpDir(), "dupes").toString()));
    }

    Path retain(Message message, Link peer) throws IOException {
        if (discard) {
            return null;
        }
        if (peer.getMyaddr() == null || peer.getMyaddr().isEmpty()) {
            throw new IOException("Missing local AKA for duplicate archive");
        }
        FTNAddr destination = peer.getMyaddr().get(0);
        byte[] bytes = PacketWriter.write(peer.getAddr(),
                                          destination,
                                          "",
                                          Collections.singletonList(message));
        synchronized (DuplicateMailArchive.class) {
            return publish(message, peer, bytes);
        }
    }

    private Path publish(Message message, Link peer, byte[] bytes) throws IOException {
        Files.createDirectories(root);
        // Ignore the newly generated packet timestamp, but retain distinct bodies and peers.
        Path index = root.resolve(fingerprint(peer, bytes) + ".dupe");
        Properties properties = new Properties();
        if (Files.exists(index)) {
            try (InputStream input = Files.newInputStream(index)) {
                properties.load(input);
            }
        } else {
            properties.setProperty("packet", PacketNames.allocate(root, root));
            properties.setProperty("peer", peer.getAddr().toString());
            properties.setProperty("kind", message.isEchomail() ? "echo" : "netmail");
            properties.setProperty("area", message.getArea() == null ? "" : message.getArea());
            properties.setProperty("msgid", message.getMsgId() == null ? "" : message.getMsgId());
            AtomicFiles.publish(index, output -> properties.store(output, "JFtn duplicate mail"));
        }
        String name = properties.getProperty("packet", "");
        if (!name.matches("[0-9a-f]{8}\\.pkt")) {
            throw new IOException("Invalid duplicate archive packet name");
        }
        Path packet = root.resolve(name);
        if (!Files.exists(packet)) {
            AtomicFiles.publish(packet, output -> output.write(bytes));
        }
        return packet;
    }

    private static String fingerprint(Link peer, byte[] packet) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(peer.getAddr().toString().getBytes(StandardCharsets.US_ASCII));
            digest.update(packet, 58, packet.length - 58);
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
