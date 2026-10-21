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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Edits only checksum directives in the directory-owned fragment, preserving other text. */
final class DirectoryCrcConfiguration {
    static final String COMPATIBILITY = "DirectoryCRCCompatibility";
    static final String IGNORE = "DirectoryIgnoreCRC";
    private final Path main;
    private final Path file;
    private String expected;
    private final boolean owned;

    DirectoryCrcConfiguration(IConfig config) throws IOException {
        main = config instanceof JFtnConfig ? ((JFtnConfig) config).getSourcePath() : null;
        file = main == null ? null : main.getParent().resolve("conf.d/30-nodelist.conf");
        expected = file == null || !Files.exists(file) ? "" : Files.readString(file);
        IConfig fragment = parse(expected);
        owned = file != null
                && values(config, COMPATIBILITY).equals(values(fragment, COMPATIBILITY))
                && values(config, IGNORE).equals(values(fragment, IGNORE));
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
            throw new IOException("Invalid directory CRC configuration", failure);
        }
        return config;
    }

    private static boolean managed(String line) {
        String declaration = line.split("#", 2)[0];
        int separator = declaration.indexOf('=');
        return separator >= 0 && Set.of("directorycrccompatibility", "directoryignorecrc")
                                    .contains(declaration.substring(0, separator)
                                                         .trim()
                                                         .toLowerCase(Locale.ROOT));
    }

    private boolean elsewhere(Path path) throws IOException {
        return Files.isRegularFile(path)
                && Files.readAllLines(path).stream().anyMatch(DirectoryCrcConfiguration::managed);
    }

    boolean writable() throws IOException {
        if (!owned || elsewhere(main)) {
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
                        && elsewhere(path)) {
                    return false;
                }
            }
        }
        return true;
    }

    void save(List<String> compatibility, List<String> ignored) throws IOException {
        if (!writable()) {
            throw new IOException("CRC settings changed externally or are declared outside conf.d/30-nodelist.conf; edit the files and restart the node");
        }
        String separator = expected.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>();
        expected.lines().filter(line -> !managed(line)).forEach(lines::add);
        compatibility.forEach(value -> lines.add(COMPATIBILITY + "=" + value));
        ignored.forEach(value -> lines.add(IGNORE + "=" + value));
        String next = String.join(separator, lines) + separator;
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".directory-crc-", ".tmp");
        try {
            Files.writeString(temporary, next);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (!writable()) {
                throw new IOException("Directory CRC configuration changed while saving");
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
