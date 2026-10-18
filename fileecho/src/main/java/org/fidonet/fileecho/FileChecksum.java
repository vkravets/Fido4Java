package org.fidonet.fileecho;

import org.fidonet.jftn.script.FileDeliveryQueue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

final class FileChecksum {
    final String crc;
    final String sha;
    final long size;

    private FileChecksum(String crc, String sha, long size) {
        this.crc = crc;
        this.sha = sha;
        this.size = size;
    }

    static FileChecksum read(Path path) throws IOException {
        if (Files.size(path) > FileDeliveryQueue.MAX_FILE_BYTES) {
            throw new IOException("Fileecho payload exceeds 128 MiB");
        }
        CRC32 crc = new CRC32();
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        long size = 0;
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > FileDeliveryQueue.MAX_FILE_BYTES) {
                    throw new IOException("Fileecho payload exceeds 128 MiB");
                }
                crc.update(buffer, 0, count);
                sha.update(buffer, 0, count);
            }
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : sha.digest()) {
            hash.append(String.format("%02x", value & 255));
        }
        return new FileChecksum(String.format("%08X", crc.getValue()), hash.toString(), size);
    }

    boolean same(FileChecksum other) {
        return size == other.size && sha.equals(other.sha) && crc.equals(other.crc);
    }
}
