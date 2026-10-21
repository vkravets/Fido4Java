package org.fidonet.jftn.plugins.ui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Declarative admin settings. Providers validate and atomically apply each command.
 * Labels are fallback metadata; clients own localization and language selection.
 */
public interface SettingsExtension {
    String id();

    View describe() throws IOException;

    void apply(String section, Map<String, String> values) throws IOException;

    final class Field {
        public final String name;
        public final String label;
        public final String type;
        public final List<String> choices;

        public Field(String name, String label, String type, List<String> choices) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.choices = List.copyOf(choices);
        }
    }

    final class Section {
        public final String id;
        public final String title;
        public final List<Field> fields;
        public final List<Map<String, String>> rows;
        public final boolean writable;

        public Section(String id,
                       String title,
                       List<Field> fields,
                       List<Map<String, String>> rows,
                       boolean writable) {
            this.id = id;
            this.title = title;
            this.fields = List.copyOf(fields);
            this.rows = rows.stream().map(Map::copyOf).collect(Collectors.toUnmodifiableList());
            this.writable = writable;
        }
    }

    final class View {
        public final String id;
        public final String title;
        public final String source;
        public final List<Section> sections;

        public View(String id, String title, String source, List<Section> sections) {
            this.id = id;
            this.title = title;
            this.source = source;
            this.sections = List.copyOf(sections);
        }
    }
}
