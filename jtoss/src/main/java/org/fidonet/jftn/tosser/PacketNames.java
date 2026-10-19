package org.fidonet.jftn.tosser;

import org.fidonet.misc.AtomicFiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** HPT-style eight-hex-digit packet names, independent of message/delivery identities. */
final class PacketNames {
    private PacketNames() {
    }

    static synchronized String allocate(Path root, Path directory) throws IOException {
        Files.createDirectories(root);
        try (FileChannel channel = FileChannel.open(root.resolve("packet-sequence.lock"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            Path sequence = root.resolve("packet.sequence");
            long serial = previous(sequence);
            String name;
            do {
                serial = Math.max(serial + 1, System.currentTimeMillis() / 1000);
                if (serial > 0xffffffffL) {
                    throw new IOException("Outbound packet name space exhausted");
                }
                name = String.format(Locale.ROOT, "%08x.pkt", serial);
            } while (Files.exists(directory.resolve(name))
                    || Files.exists(directory.resolve(name + ".owner")));
            byte[] bytes = Long.toString(serial).getBytes(StandardCharsets.US_ASCII);
            AtomicFiles.replace(sequence, output -> output.write(bytes));
            return name;
        }
    }

    private static long previous(Path sequence) throws IOException {
        if (!Files.exists(sequence)) {
            return 0;
        }
        try {
            long value = Long.parseLong(Files.readString(sequence, StandardCharsets.US_ASCII));
            if (value < 0 || value > 0xffffffffL) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IOException("Corrupt outbound packet sequence", e);
        }
    }
}
