package org.fidonet.packet;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** HPT splits attach subjects on commas/spaces and strips directories from the wire header. */
public final class AttachmentNames {
    private AttachmentNames() {
    }

    public static List<String> tokens(String subject) throws IOException {
        if (subject == null || subject.isBlank() || subject.length() > 8192
                || subject.chars()
                          .anyMatch(character -> character < 32 && character != 9
                                  || character == 127)) {
            throw new IOException("Invalid file attach subject");
        }
        List<String> names = Arrays.stream(subject.trim().split("[,\\t ]+"))
                                   .filter(value -> !value.isEmpty())
                                   .collect(Collectors.toList());
        if (names.isEmpty() || names.size() > 128) {
            throw new IOException("Invalid attachment count");
        }
        return names;
    }

    public static String basename(String value) throws IOException {
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(Math.max(normalized.lastIndexOf('/'),
                                                    normalized.lastIndexOf(':'))
                                           + 1);
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("*")
                || name.contains("?")) {
            throw new IOException("File attach requires exact filenames");
        }
        return name;
    }

    public static String wireSubject(String subject) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String token : tokens(subject)) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(basename(token));
        }
        return result.toString();
    }
}
