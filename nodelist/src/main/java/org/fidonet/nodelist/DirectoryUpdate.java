package org.fidonet.nodelist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Publishes validated base editions and recovers catalog imports interrupted after publication. */
public final class DirectoryUpdate {
    private static final long MAX_BYTES = 128L * 1024 * 1024;

    private DirectoryUpdate() {
    }

    public static DirectoryImportResult install(DirectoryCatalog catalog,
                                                DirectorySource source,
                                                Path incoming,
                                                boolean diff)
            throws IOException {
        if (diff) {
            source = source.withIgnoredChecksum(false);
        }
        Path base = source.getPath();
        if (base.equals(incoming.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Incoming file must be separate from the retained base");
        }
        Files.createDirectories(base.getParent());
        Path lockPath = base.resolveSibling(base.getFileName() + ".update.lock");
        try (FileChannel channel = FileChannel.open(lockPath,
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw new IOException("Another directory update is running");
                }
                return installLocked(catalog, source, incoming, diff);
            } catch (OverlappingFileLockException e) {
                throw new IOException("Another directory update is running", e);
            }
        }
    }

    private static DirectoryImportResult installLocked(DirectoryCatalog catalog,
                                                       DirectorySource source,
                                                       Path incoming,
                                                       boolean diff)
            throws IOException {
        Path base = source.getPath();
        String inputHash = fingerprint(incoming);
        Path journal = base.resolveSibling(base.getFileName() + ".update");
        if (Files.isRegularFile(journal) && Files.size(journal) < 1024
                && Files.isRegularFile(base)) {
            String expected = inputHash + "\n" + fingerprint(base);
            if (expected.equals(Files.readString(journal))) {
                return catalog.importSource(source);
            }
        }
        Path decoded = Files.createTempFile(base.getParent(), ".directory-input-", ".tmp");
        Path candidate = Files.createTempFile(base.getParent(), ".directory-update-", ".tmp");
        try {
            unpack(incoming, decoded);
            if (!inputHash.equals(fingerprint(incoming))) {
                throw new IOException("Incoming directory changed while unpacking");
            }
            if (diff) {
                validate(base, source, true);
                DirectoryDiff.apply(base, decoded, candidate);
            } else {
                Files.copy(decoded, candidate, StandardCopyOption.REPLACE_EXISTING);
            }
            validate(candidate, source, diff || source.isChecksumRequired());
            catalog.validateSource(source.atPath(candidate, diff || source.isChecksumRequired()));
            String value = inputHash + "\n" + fingerprint(candidate);
            publishJournal(journal, value);
            force(candidate);
            Files.move(candidate,
                       base,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            // If SQL fails, the verified new base and journal survive. Redelivery imports it
            // directly instead of attempting to apply the same diff twice.
            return catalog.importSource(source);
        } finally {
            Files.deleteIfExists(decoded);
            Files.deleteIfExists(candidate);
        }
    }

    private static void validate(Path file, DirectorySource source, boolean checksum)
            throws IOException {
        DirectorySource candidate = source.atPath(file, checksum);
        try (InputStream input = Files.newInputStream(file)) {
            DirectoryParser.parse(input, candidate, entry -> {
            });
        }
    }

    private static void unpack(Path incoming, Path target) throws IOException {
        if (Files.size(incoming) > MAX_BYTES) {
            throw new IOException("Incoming directory exceeds 128 MiB");
        }
        try (InputStream input = Files.newInputStream(incoming);
                DirectoryInput content = DirectoryInput.open(input);
                OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[65536];
            long total = 0;
            int count;
            while ((count = content.content().read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) {
                    throw new IOException("Expanded directory exceeds 128 MiB");
                }
                output.write(buffer, 0, count);
            }
            content.finish();
        }
    }

    private static String fingerprint(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            updateDigest(input, digest);
        }
        StringBuilder value = new StringBuilder();
        for (byte octet : digest.digest()) {
            value.append(String.format("%02x", octet & 255));
        }
        return value.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void updateDigest(InputStream input, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        long total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) {
                throw new IOException("Directory input exceeds 128 MiB");
            }
            digest.update(buffer, 0, count);
        }
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void publishJournal(Path journal, String value) throws IOException {
        Path temporary = Files.createTempFile(journal.getParent(), ".directory-journal-", ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.US_ASCII);
            force(temporary);
            Files.move(temporary,
                       journal,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
