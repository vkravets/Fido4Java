package org.fidonet.jftn.ui;

import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.rpc.ExtensionField;
import org.fidonet.jftn.rpc.ExtensionSection;
import org.fidonet.jftn.rpc.ExtensionSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps optional provider contracts to transport DTOs, without a dependency on plugin classes. */
final class NodeExtensions {
    private NodeExtensions() {
    }

    static List<ExtensionSettings> describe(List<SettingsExtension> providers) throws IOException {
        List<ExtensionSettings> result = new ArrayList<>();
        for (SettingsExtension provider : providers) {
            SettingsExtension.View view = provider.describe();
            List<ExtensionSection> sections = new ArrayList<>();
            for (SettingsExtension.Section section : view.sections) {
                List<ExtensionField> fields = new ArrayList<>();
                for (SettingsExtension.Field field : section.fields) {
                    fields.add(new ExtensionField(field.name,
                                                  field.label,
                                                  field.type,
                                                  field.choices));
                }
                sections.add(new ExtensionSection(section.id,
                                                  section.title,
                                                  fields,
                                                  section.rows,
                                                  section.writable));
            }
            result.add(new ExtensionSettings(view.id,
                                             view.title,
                                             view.source,
                                             sections,
                                             view.translations));
        }
        return result;
    }

    static void save(List<SettingsExtension> providers,
                     String id,
                     String section,
                     Map<String, String> values)
            throws IOException {
        if (values == null || values.size() > 32
                || values.entrySet()
                         .stream()
                         .anyMatch(entry -> entry.getKey() == null || entry.getKey().length() > 64
                                 || entry.getValue() == null || entry.getValue().length() > 4096)) {
            throw new IllegalArgumentException("Invalid extension settings request");
        }
        for (SettingsExtension provider : providers) {
            if (provider.id().equals(id)) {
                provider.apply(section, Map.copyOf(values));
                return;
            }
        }
        throw new IllegalArgumentException("Settings extension is unavailable");
    }
}
