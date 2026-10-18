package org.fidonet.fileecho;

import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
        for (String key : List.of("area", "file", "origin", "from", "crc", "path", "seenby")) {
            if (!fields.containsKey(key) || fields.get(key).stream().anyMatch(String::isEmpty)) {
                throw new IOException("Missing TIC keyword: " + key);
            }
        }
        for (String key : List.of("area", "file", "origin", "from", "crc", "size", "pw", "to")) {
            if (values(key).size() > 1) {
                throw new IOException("Duplicate TIC keyword: " + key);
            }
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
        FTNAddr address = new FTNAddr(text);
        if (!address.isValid()) {
            throw new IOException("Invalid TIC address");
        }
        return address;
    }
}
