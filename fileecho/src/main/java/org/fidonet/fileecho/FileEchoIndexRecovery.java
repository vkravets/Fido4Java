package org.fidonet.fileecho;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Reads archived TIC metadata without importing mail or modifying delivery journals. */
public final class FileEchoIndexRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(FileEchoIndexRecovery.class);

    private FileEchoIndexRecovery() {
    }

    public static List<Map<String, Object>> recover(String area,
                                                    Path directory,
                                                    Path archive,
                                                    Charset charset)
            throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("Area must be an existing nonsymlink directory");
        }
        if (Files.isSymbolicLink(archive) || !Files.isDirectory(archive)) {
            throw new IOException("Received archive must be an existing nonsymlink directory");
        }
        Map<String, Path> files = payloads(directory);
        Map<String, FileChecksum> checksums = new LinkedHashMap<>();
        Map<String, List<String>> descriptions = new LinkedHashMap<>();
        Map<String, Instant> dates = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(archive)) {
            for (Path path : (Iterable<Path>) paths.sorted()::iterator) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !path.getFileName()
                                .toString()
                                .toLowerCase(Locale.ROOT)
                                .endsWith(".tic")) {
                    continue;
                }
                TicDocument tic = read(path, charset);
                if (tic == null || !area.equalsIgnoreCase(tic.value("area"))) {
                    continue;
                }
                match(tic, files, checksums, descriptions, dates);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.getValue().getFileName().toString());
            row.put("descriptions", descriptions.getOrDefault(entry.getKey(), List.of()));
            row.put("size", Files.size(entry.getValue()));
            row.put("date", dates.get(entry.getKey()));
            result.add(row);
        }
        return result;
    }

    private static Map<String, Path> payloads(Path directory) throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path file : (Iterable<Path>) paths.sorted()::iterator) {
                String name = file.getFileName().toString();
                String key = name.toLowerCase(Locale.ROOT);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || name.startsWith(".")
                        || key.equals("file.bbs") || key.equals("files.bbs")) {
                    continue;
                }
                if (name.matches(".*[\\s\\p{Cntrl}].*")) {
                    throw new IOException("Index cannot represent payload filename: " + name);
                }
                if (files.putIfAbsent(key, file) != null) {
                    throw new IOException("Case-insensitive filename collision: " + name);
                }
            }
        }
        return files;
    }

    private static TicDocument read(Path path, Charset charset) {
        try {
            return new TicDocument(path, charset);
        } catch (IOException | IllegalArgumentException failure) {
            LOG.warn("Skipped invalid archived TIC {}: {}",
                     path.getFileName(),
                     failure.getMessage());
            return null;
        }
    }

    private static void match(TicDocument tic,
                              Map<String, Path> files,
                              Map<String, FileChecksum> checksums,
                              Map<String, List<String>> descriptions,
                              Map<String, Instant> dates)
            throws IOException {
        String key = tic.value("file").toLowerCase(Locale.ROOT);
        Path file = files.get(key);
        if (file == null) {
            return;
        }
        FileChecksum checksum = checksums.get(key);
        if (checksum == null) {
            checksum = FileChecksum.read(file);
            checksums.put(key, checksum);
        }
        if (!checksum.crc.equalsIgnoreCase(tic.value("crc")) || !tic.matchesSize(checksum.size)) {
            return;
        }
        List<String> text = new ArrayList<>(tic.values("desc"));
        text.addAll(tic.values("ldesc"));
        List<String> previous = descriptions.putIfAbsent(key, text);
        if (previous != null && !previous.equals(text)) {
            throw new IOException("Conflicting archived descriptions for " + file.getFileName());
        }
        Instant date = tic.indexDate();
        if (date != null) {
            dates.merge(key,
                        date,
                        (previousDate, next) -> previousDate.isBefore(next) ? previousDate : next);
        }
    }
}
