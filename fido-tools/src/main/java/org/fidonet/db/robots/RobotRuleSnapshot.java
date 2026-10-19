package org.fidonet.db.robots;

import org.fidonet.config.JFtnConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** Versioned file configuration retaining password verifiers and disabled rules. */
public final class RobotRuleSnapshot {
    private RobotRuleSnapshot() {
    }

    static List<RobotAccessRule> read(JFtnConfig config) {
        if (!"1".equals(config.getValue("RobotAccessFormat"))) {
            throw new IllegalArgumentException("Unsupported RobotAccessFormat");
        }
        List<RobotAccessRule> result = new ArrayList<>();
        List<String> entries = config.getValuesAsList("RobotAccessRule");
        if (entries != null && entries.equals(List.of("none"))) {
            return List.of();
        }
        if (entries != null) {
            entries.forEach(entry -> result.add(decode(entry)));
        }
        return result;
    }

    private static RobotAccessRule decode(String entry) {
        try {
            String[] fields = entry.split("\\|", -1);
            if (fields.length != 6 || !(fields[5].equals("true") || fields[5].equals("false"))) {
                throw new IllegalArgumentException();
            }
            RobotService service = RobotService.valueOf(fields[0]);
            String credential = decodeField(fields[2]);
            if (service != RobotService.Tic) {
                RobotPassword.validate(credential);
            } else if (credential.isEmpty() || credential.length() > 256
                    || credential.chars().anyMatch(c -> c < 32 || c == 127)) {
                throw new IllegalArgumentException();
            }
            return new RobotAccessRule(service,
                                       fields[1],
                                       credential,
                                       decodeField(fields[3]),
                                       fields[4],
                                       Boolean.parseBoolean(fields[5]));
        } catch (IllegalArgumentException | IllegalStateException | IOException failure) {
            // Never include encoded or decoded credentials in diagnostics.
            throw new IllegalArgumentException("Invalid RobotAccessRule snapshot entry");
        }
    }

    private static String decodeField(String field) throws IOException {
        if (field.length() > 24000) {
            throw new IllegalArgumentException();
        }
        return StandardCharsets.UTF_8.newDecoder()
                                     .decode(ByteBuffer.wrap(Base64.getDecoder().decode(field)))
                                     .toString();
    }

    private static String encodeField(String field) {
        return Base64.getEncoder().encodeToString(field.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(RobotAccessRule rule) {
        return rule.getService()
                   .name()
               + "|"
               + rule.getAddress()
               + "|"
               + encodeField(rule.credential())
               + "|"
               + encodeField(rule.getMasks())
               + "|"
               + rule.getAccess()
               + "|"
               + rule.isEnabled();
    }

    /** Publishes a complete new fragment without replacing an existing destination. */
    public static int write(Path destination, RobotRules rules) throws IOException {
        List<RobotAccessRule> snapshot = new ArrayList<>();
        for (RobotService service : RobotService.values()) {
            snapshot.addAll(rules.list(service));
        }
        snapshot.sort(Comparator.comparing((RobotAccessRule rule) -> rule.getService().name())
                                .thenComparing(RobotAccessRule::getAddress));
        StringBuilder text = new StringBuilder("# Private robot access snapshot. Contains credentials; keep it out of version control.\n"
                + "# Format 1: service|address|base64(credential)|base64(masks)|access|enabled\n"
                + "# Snapshot replaces all legacy AreaFixPeer/FileFixPeer/TicPeer rules.\n"
                + "RobotAccessSource=files\nRobotAccessFormat=1\n");
        snapshot.forEach(rule -> text.append("RobotAccessRule=").append(encode(rule)).append('\n'));
        if (snapshot.isEmpty()) {
            text.append("RobotAccessRule=none\n");
        }
        publish(destination, text.toString());
        return snapshot.size();
    }

    private static void publish(Path destination, String text) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = temporary(parent);
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path temporary(Path parent) throws IOException {
        if (Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return Files.createTempFile(parent,
                                        ".robot-access-",
                                        ".tmp",
                                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        // Windows inherits the installation directory's ACL.
        return Files.createTempFile(parent, ".robot-access-", ".tmp");
    }
}
