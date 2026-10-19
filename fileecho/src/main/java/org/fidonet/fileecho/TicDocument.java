package org.fidonet.fileecho;

import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded TIC parsing. Unknown fields retain their original spelling and order. */
final class TicDocument {
    final List<String> lines;
    private final Map<String, List<String>> fields = new LinkedHashMap<>();

    TicDocument(Path path, Charset charset) throws IOException {
        if (Files.size(path) > 65536) {
            throw new IOException("TIC exceeds 64 KiB");
        }
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(65537);
            if (bytes.length > 65536) {
                throw new IOException("TIC exceeds 64 KiB");
            }
            lines = new ArrayList<>();
            for (String line : new String(bytes, charset).split("[\\r\\n]+")) {
                if (line.length() > 1024
                        || line.matches(".*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*")) {
                    throw new IOException("Invalid TIC line");
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] pair = line.trim().split("\\s+", 2);
                fields.computeIfAbsent(pair[0].toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                      .add(pair.length == 2 ? pair[1] : "");
                lines.add(line);
            }
        }
        for (String key : List.of("area", "file", "origin", "from", "crc")) {
            if (!fields.containsKey(key) || fields.get(key).stream().anyMatch(String::isEmpty)) {
                throw new IOException("Missing TIC keyword: " + key);
            }
        }
        for (String key : List.of("area",
                                  "file",
                                  "origin",
                                  "from",
                                  "crc",
                                  "size",
                                  "date",
                                  "pw",
                                  "to")) {
            if (values(key).size() > 1) {
                throw new IOException("Duplicate TIC keyword: " + key);
            }
        }
        for (String value : values("path")) {
            validatePath(value);
        }
        for (String key : List.of("size", "date")) {
            if (!values(key).isEmpty()) {
                nonnegativeNumber(value(key), key);
            }
        }
    }

    private void validatePath(String value) throws IOException {
        String[] parts = value.trim().split("\\s+", 3);
        address(parts[0]);
        if (parts.length < 2 || !parts[1].matches("[0-9]+")) {
            throw new IOException("TIC Path requires a nonnegative Unix timestamp");
        }
        try {
            Long.parseLong(parts[1]);
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid TIC Path timestamp", failure);
        }
    }

    private static long nonnegativeNumber(String value, String key) throws IOException {
        try {
            if (!value.matches("[0-9]+")) {
                throw new NumberFormatException("Expected unsigned decimal");
            }
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid TIC " + key, failure);
        }
    }

    FTNAddr recipient() throws IOException {
        String value = value("to");
        return address(value.substring(value.lastIndexOf(',') + 1).trim());
    }

    boolean matchesSize(long size) {
        return value("size").isEmpty() || Long.parseLong(value("size")) == size;
    }

    /** TIC Date is the file date; old senders can fall back to the first recorded hop. */
    Instant indexDate() {
        String date = value("date");
        if (date.isEmpty() && values("path").isEmpty()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(date.isEmpty()
                    ? value("path").trim().split("\\s+", 3)[1]
                    : date));
        } catch (DateTimeException failure) {
            return null;
        }
    }

    String value(String key) {
        return values(key).isEmpty() ? "" : values(key).get(0);
    }

    List<String> values(String key) {
        return fields.getOrDefault(key, List.of());
    }

    Set<FTNAddr> addresses(String key) throws IOException {
        Set<FTNAddr> result = new LinkedHashSet<>();
        for (String value : values(key)) {
            result.add(address(value));
        }
        return result;
    }

    static FTNAddr address(String value) throws IOException {
        String text = value.trim().split("\\s+")[0];
        text = text.substring(text.lastIndexOf('#') + 1).split("@", 2)[0];
        FTNAddr address;
        try {
            address = new FTNAddr(text);
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid TIC address", failure);
        }
        if (!address.isValid()) {
            throw new IOException("Invalid TIC address");
        }
        return address;
    }
}
