package org.fidonet.packet;

import org.fidonet.types.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Stable identity excluding mutable distribution and tracking lines. */
public final class MailIdentity {
    private MailIdentity() {
    }

    public static String of(Message message) {
        String area = message.getArea() == null ? "" : message.getArea().toLowerCase(Locale.ROOT);
        String id = message.getMsgId();
        String value = area + "\0"
                + (message.isEchomail()
                        ? ""
                        : message.getFAddr() + "\0" + message.getTAddr() + "\0")
                + (id == null || id.isEmpty()
                        ? message.getFAddr() + "\0" + message.getFrom() + "\0" + message.getTo()
                                + "\0" + message.getSubject() + "\0" + message.getMsgDate() + "\0"
                                + message.getBody().getBody()
                        : id);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                                         .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) {
                result.append(String.format("%02x", b & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
