package org.fidonet.nodelist;

import org.fidonet.config.BaseConfig;
import org.fidonet.config.IConfig;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Edits owned directory directives in the directory-owned fragment, preserving other text. */
final class DirectoryConfiguration {
    static final String COMPATIBILITY = "DirectoryCRCCompatibility";
    static final String IGNORE = "DirectoryIgnoreCRC";
    static final String SOURCES = "DirectorySource";
    private static final Set<String> CRC_KEYS = Set.of(COMPATIBILITY, IGNORE);
    private final Path main;
    private final Path file;
    private String expected;
    private final Set<String> ownedKeys;

    DirectoryConfiguration(IConfig config) throws IOException {
        main = config instanceof JFtnConfig ? ((JFtnConfig) config).getSourcePath() : null;
        file = main == null ? null : main.getParent().resolve("conf.d/30-nodelist.conf");
        expected = file == null || !Files.exists(file) ? "" : Files.readString(file);
        IConfig fragment = parse(expected);
        ownedKeys = new HashSet<>();
        for (String key : List.of(COMPATIBILITY, IGNORE, SOURCES)) {
            if (file != null && values(config, key).equals(values(fragment, key))) {
                ownedKeys.add(key);
            }
        }
    }

    static List<String> values(IConfig config, String key) {
        List<String> values = config.getValuesAsList(key);
        return values == null ? List.of() : List.copyOf(values);
    }

    static IConfig parse(String text) throws IOException {
        BaseConfig config = new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
        try {
            config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (ParseConfigException failure) {
            throw new IOException("Invalid directory configuration", failure);
        }
        return config;
    }

    private static boolean managed(String line, Set<String> keys) {
        String declaration = line.split("#", 2)[0];
        int separator = declaration.indexOf('=');
        if (separator < 0) {
            return false;
        }
        String key = declaration.substring(0, separator).trim();
        return keys.stream().anyMatch(key::equalsIgnoreCase);
    }

    private boolean elsewhere(Path path, Set<String> keys) throws IOException {
        return Files.isRegularFile(path)
                && Files.readAllLines(path).stream().anyMatch(line -> managed(line, keys));
    }

    boolean writable() throws IOException {
        return writable(CRC_KEYS);
    }

    boolean sourcesWritable() throws IOException {
        return writable(Set.of(SOURCES));
    }

    private boolean writable(Set<String> keys) throws IOException {
        if (!ownedKeys.containsAll(keys) || elsewhere(main, keys)) {
            return false;
        }
        String current = Files.exists(file) ? Files.readString(file) : "";
        if (!current.equals(expected)) {
            return false;
        }
        if (!Files.isDirectory(file.getParent())) {
            return true;
        }
        try (Stream<Path> paths = Files.list(file.getParent())) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!path.equals(file) && path.getFileName().toString().endsWith(".conf")
                        && elsewhere(path, keys)) {
                    return false;
                }
            }
        }
        return true;
    }

    void save(List<String> compatibility, List<String> ignored) throws IOException {
        Map<String, List<String>> declarations = new LinkedHashMap<>();
        declarations.put(COMPATIBILITY, compatibility);
        declarations.put(IGNORE, ignored);
        save(declarations);
    }

    void saveSources(List<String> definitions) throws IOException {
        save(Map.of(SOURCES, definitions));
    }

    private void save(Map<String, List<String>> declarations) throws IOException {
        Set<String> keys = declarations.keySet();
        if (!writable(keys)) {
            throw new IOException("Directory settings changed externally or are declared outside conf.d/30-nodelist.conf; edit the files and restart the node");
        }
        String separator = expected.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>();
        expected.lines().filter(line -> !managed(line, keys)).forEach(lines::add);
        declarations.forEach((key,
                              values) -> values.forEach(value -> lines.add(key + "=" + value)));
        String next = String.join(separator, lines) + separator;
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".directory-config-", ".tmp");
        try {
            Files.writeString(temporary, next);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (!writable(keys)) {
                throw new IOException("Directory configuration changed while saving");
            }
            Files.move(temporary,
                       file,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            expected = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
