package org.fidonet.misc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileDigests {
    private FileDigests() {
    }

    public static byte[] sha256(Path path, long maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) {
            throw new IOException("File must be regular and within its size limit");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximum) {
                    throw new IOException("File grew beyond its size limit");
                }
                digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }
}
