package org.fidonet.jftn.tosser.jam;

import org.fidonet.types.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Detects edits even when an editor retains MSGID. Excludes delivery/read status. */
final class JamFingerprint {
    private JamFingerprint() {
    }

    static String of(Message message, int attributes) {
        String value = message.getFrom()
                       + "\0"
                       + message.getTo()
                       + "\0"
                       + message.getFAddr()
                       + "\0"
                       + message.getTAddr()
                       + "\0"
                       + message.getSubject()
                       + "\0"
                       + message.getMsgDate()
                       + "\0"
                       + message.getBody()
                       + "\0"
                       + (attributes & ~0xc0000018);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                                        .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                result.append(String.format("%02x", b & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
