package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.plugins.ui.SettingsExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Combines directory-owned settings under one optional plugin extension. */
final class DirectorySettings implements SettingsExtension {
    private final DirectoryCrcSettings crc;
    private final DirectorySourceSettings sources;

    DirectorySettings(DirectoryService service, IConfig config, DirectoryConfiguration file) {
        crc = new DirectoryCrcSettings(service, config, file);
        sources = new DirectorySourceSettings(service, config, file);
    }

    @Override
    public String id() {
        return "nodelist";
    }

    @Override
    public synchronized View describe() throws IOException {
        View checksum = crc.describe();
        List<Section> sections = new ArrayList<>();
        sections.add(sources.describe());
        sections.addAll(checksum.sections);
        return new View(id(), checksum.title, checksum.source, sections);
    }

    @Override
    public synchronized void apply(String section, Map<String, String> values) throws IOException {
        if ("sources".equals(section)) {
            sources.apply(values);
            return;
        }
        crc.apply(section, values);
    }

    @Override
    public synchronized Map<String, List<String>> appliedConfiguration() {
        Map<String, List<String>> result = new HashMap<>(crc.appliedConfiguration());
        result.put("directorysource", sources.definitions());
        return Map.copyOf(result);
    }
}
