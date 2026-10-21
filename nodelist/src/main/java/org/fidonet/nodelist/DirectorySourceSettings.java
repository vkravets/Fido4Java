package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.plugins.ui.SettingsExtension.Field;
import org.fidonet.jftn.plugins.ui.SettingsExtension.Section;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates source edits before atomically persisting and publishing the registry. */
final class DirectorySourceSettings {
    private static final List<String> KEYS = List.of("id",
                                                     "format",
                                                     "path",
                                                     "zone",
                                                     "net",
                                                     "charset",
                                                     "requireChecksum");
    private final DirectoryService service;
    private final DirectoryConfiguration file;
    private List<String> definitions;

    DirectorySourceSettings(DirectoryService service, IConfig config, DirectoryConfiguration file) {
        this.service = service;
        this.file = file;
        definitions = DirectoryConfiguration.values(config, DirectoryConfiguration.SOURCES);
    }

    Section describe() throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String definition : definitions) {
            DirectorySource source = DirectorySource.parse(definition);
            rows.add(Map.of("id",
                            source.getId(),
                            "format",
                            source.getFormat().name(),
                            "path",
                            definition.split("\\|", -1)[2].trim(),
                            "zone",
                            Integer.toString(source.getDefaultZone()),
                            "net",
                            Integer.toString(source.getDefaultNet()),
                            "charset",
                            source.getCharset().name(),
                            "requireChecksum",
                            Boolean.toString(source.isChecksumRequired())));
        }
        List<Field> fields = List.of(text("id", "Source ID"),
                                     new Field("format",
                                               "List format",
                                               "select",
                                               Arrays.stream(DirectorySource.Format.values())
                                                     .map(Enum::name)
                                                     .collect(Collectors.toList())),
                                     text("path", "List file"),
                                     text("zone", "Default zone"),
                                     text("net", "Default net"),
                                     text("charset", "List encoding"),
                                     new Field("requireChecksum",
                                               "Require header CRC",
                                               "select",
                                               List.of("true", "false")));
        return new Section("sources", "List sources", fields, rows, file.sourcesWritable());
    }

    private static Field text(String name, String label) {
        return new Field(name, label, "text", List.of());
    }

    List<String> definitions() {
        return definitions;
    }

    void apply(Map<String, String> values) throws IOException {
        if (!values.keySet().equals(Set.copyOf(KEYS))) {
            throw new IllegalArgumentException("Unknown directory source fields");
        }
        for (String value : values.values()) {
            if (value == null || value.isBlank() || value.length() > 4096
                    || value.chars()
                            .anyMatch(character -> Character.isISOControl(character)
                                    || character == '|' || character == '#')) {
                throw new IllegalArgumentException("Expected nonempty source fields without configuration delimiters");
            }
        }
        String declaration = KEYS.stream()
                                 .map(key -> values.get(key).trim())
                                 .collect(Collectors.joining("|"));
        DirectorySource edited = DirectorySource.parse(declaration);
        List<String> next = new ArrayList<>(definitions);
        next.removeIf(value -> DirectorySource.parse(value).getId().equals(edited.getId()));
        next.add(declaration);
        List<DirectorySource> parsed = next.stream()
                                           .map(DirectorySource::parse)
                                           .collect(Collectors.toList());
        synchronized (service) {
            service.validateSourceChange(parsed);
            file.saveSources(next);
            service.replaceSources(parsed);
        }
        definitions = List.copyOf(next);
    }
}
