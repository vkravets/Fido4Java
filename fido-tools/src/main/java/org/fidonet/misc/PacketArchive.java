package org.fidonet.misc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validates the complete bundle on disk before exposing one packet at a time. */
public final class PacketArchive implements AutoCloseable {
    private final Path directory;
    private final List<PacketFile> packets = new ArrayList<>();
    private long total;
    private int remaining;

    PacketArchive(Path source) throws IOException {
        directory = Files.createTempDirectory("jftn-packets-");
        try {
            stage(source);
            remaining = packets.size();
        } catch (IOException | RuntimeException failure) {
            cleanupAfterFailure(failure);
            throw failure;
        }
    }

    private void cleanupAfterFailure(Exception failure) {
        try {
            close();
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private void stage(Path source) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            stageEntries(zip);
        }
    }

    private void stageEntries(ZipFile zip) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        int count = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (++count > Zipper.MAX_ENTRIES) {
                throw new IOException("Too many archive entries");
            }
            if (!entry.isDirectory()) {
                stageEntry(zip, entry);
            }
        }
    }

    private void stageEntry(ZipFile zip, ZipEntry entry) throws IOException {
        if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".pkt")) {
            throw new IOException("Non-packet entry in mail bundle: " + entry.getName());
        }
        if (entry.getSize() > Zipper.MAX_PACKET_BYTES) {
            throw new IOException("Packet exceeds size limit");
        }
        Path target = directory.resolve(packets.size() + ".pkt");
        packets.add(new PacketFile(entry.getName(), target));
        try (InputStream input = zip.getInputStream(entry);
                OutputStream output = Files.newOutputStream(target)) {
            copyPacket(input, output);
        }
    }

    private void copyPacket(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
            total += count;
            if (size > Zipper.MAX_PACKET_BYTES || total > Zipper.MAX_BUNDLE_BYTES) {
                throw new IOException("Unpacked data exceeds size limit");
            }
            output.write(buffer, 0, count);
        }
    }

    public PktTemp readNext() throws IOException {
        if (remaining == 0) {
            return null;
        }
        PacketFile packet = packets.get(--remaining);
        return new PktTemp(packet.name, ByteBuffer.wrap(Files.readAllBytes(packet.path)));
    }

    @Override
    public void close() throws IOException {
        IOException failure = deletePackets();
        failure = delete(directory, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private IOException deletePackets() {
        IOException failure = null;
        for (PacketFile packet : packets) {
            failure = delete(packet.path, failure);
        }
        return failure;
    }

    private IOException delete(Path path, IOException previous) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            if (previous == null) {
                return failure;
            }
            previous.addSuppressed(failure);
        }
        return previous;
    }

    private static final class PacketFile {
        private final String name;
        private final Path path;

        private PacketFile(String name, Path path) {
            this.name = name;
            this.path = path;
        }
    }
}
