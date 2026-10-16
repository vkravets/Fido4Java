package org.fidonet.db.robots;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class RobotPassword {
    private static final int ITERATIONS = 600000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RobotPassword() {
    }

    static String hash(String value) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$"
               + ITERATIONS
               + "$"
               + Base64.getEncoder()
                       .encodeToString(salt)
               + "$"
               + Base64.getEncoder()
                       .encodeToString(derive(value, salt, ITERATIONS));
    }

    static boolean verify(String stored, String candidate) {
        validate(stored);
        String[] parts = stored.split("\\$", -1);
        return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                                     derive(candidate,
                                            Base64.getDecoder().decode(parts[2]),
                                            Integer.parseInt(parts[1])));
    }

    static void validate(String stored) {
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) {
            throw new IllegalStateException("Invalid robot password verifier");
        }
        int iterations = Integer.parseInt(parts[1]);
        if (iterations < 100000 || iterations > 2000000) {
            throw new IllegalStateException("Invalid robot password hash parameters");
        }
        if (Base64.getDecoder().decode(parts[2]).length != 16
                || Base64.getDecoder().decode(parts[3]).length != 32) {
            throw new IllegalArgumentException("Invalid robot password hash size");
        }
    }

    private static byte[] derive(String value, byte[] salt, int iterations) {
        char[] characters = value.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                   .generateSecret(spec)
                                   .getEncoded();
        } catch (Exception failure) {
            throw new IllegalStateException("Robot password hashing unavailable", failure);
        } finally {
            spec.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }
}
