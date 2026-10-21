package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.plugins.ui.SettingsExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Declarative settings exposed only by the active directory service. */
final class DirectoryCrcSettings implements SettingsExtension {
    private final DirectoryService service;
    private final DirectoryCrcConfiguration file;
    private List<String> compatibility;
    private List<String> ignored;

    DirectoryCrcSettings(DirectoryService service, IConfig config, DirectoryCrcConfiguration file) {
        this.service = service;
        this.file = file;
        compatibility = DirectoryCrcConfiguration.values(config,
                                                         DirectoryCrcConfiguration.COMPATIBILITY);
        ignored = DirectoryCrcConfiguration.values(config, DirectoryCrcConfiguration.IGNORE);
    }

    @Override
    public String id() {
        return "nodelist";
    }

    @Override
    public synchronized View describe() throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String rule : compatibility) {
            String[] fields = rule.split("\\|", -1);
            rows.add(Map.of("source",
                            fields[0].trim(),
                            "mask",
                            fields[1].trim(),
                            "mode",
                            DirectoryCrcMode.parse(fields[2]).value));
        }
        List<Field> fields = List.of(new Field("source", "Source", "select", service.sourceIds()),
                                     new Field("mask", "Filename mask", "text", List.of()),
                                     new Field("mode",
                                               "CRC mode",
                                               "select",
                                               List.of("XMODEM-CRC", "ARC-CRC")));
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("crc", "CRC checks", fields, rows, file.writable()));
        if (!ignored.isEmpty()) {
            List<Map<String, String>> exceptions = new ArrayList<>();
            for (String rule : ignored) {
                String[] parts = rule.split("\\|", -1);
                exceptions.add(Map.of("source", parts[0].trim(), "mask", parts[1].trim()));
            }
            sections.add(new Section("ignored-crc",
                                     "Legacy CRC exceptions",
                                     fields.subList(0, 2),
                                     exceptions,
                                     false));
        }
        Map<String, String> ukrainian = Map.of("Node directory",
                                               "Каталог вузлів",
                                               "CRC checks",
                                               "Перевірка CRC",
                                               "Source",
                                               "Джерело",
                                               "Filename mask",
                                               "Маска імені файлу",
                                               "CRC mode",
                                               "Режим CRC",
                                               "XMODEM-CRC",
                                               "XMODEM-CRC",
                                               "ARC-CRC",
                                               "ARC-CRC",
                                               "Legacy CRC exceptions",
                                               "Старі винятки перевірки CRC");
        return new View(id(),
                        "Node directory",
                        "conf.d/30-nodelist.conf",
                        sections,
                        Map.of("uk",
                               ukrainian,
                               "en",
                               Map.of("XMODEM-CRC", "XMODEM-CRC", "ARC-CRC", "ARC-CRC")));
    }

    @Override
    public synchronized void apply(String section, Map<String, String> values) throws IOException {
        if (!"crc".equals(section) || !values.keySet().equals(Set.of("source", "mask", "mode"))) {
            throw new IllegalArgumentException("Unknown directory CRC section or fields");
        }
        String id = values.get("source").trim();
        String mask = values.get("mask").trim().toUpperCase(Locale.ROOT);
        if (!service.sourceIds().contains(id) || !mask.matches("[A-Za-z0-9_.*?+-]{1,128}")
                || !mask.matches(".*[A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Expected a known source and a safe filename mask");
        }
        String mode = DirectoryCrcMode.parse(values.get("mode")).value;
        String pair = id + "|" + mask;
        List<String> next = new ArrayList<>(compatibility);
        next.removeIf(rule -> samePair(rule, id, mask));
        next.add(pair + "|" + mode);
        List<String> remaining = new ArrayList<>(ignored);
        remaining.removeIf(rule -> samePair(rule, id, mask));
        StringBuilder declarations = new StringBuilder();
        next.forEach(rule -> declarations.append(DirectoryCrcConfiguration.COMPATIBILITY)
                                         .append('=')
                                         .append(rule)
                                         .append('\n'));
        remaining.forEach(rule -> declarations.append(DirectoryCrcConfiguration.IGNORE)
                                              .append('=')
                                              .append(rule)
                                              .append('\n'));
        DirectoryChecksumExceptions policy = new DirectoryChecksumExceptions(DirectoryCrcConfiguration.parse(declarations.toString()),
                                                                             Set.copyOf(service.sourceIds()));
        file.save(next, remaining);
        service.setChecksumPolicy(policy);
        compatibility = List.copyOf(next);
        ignored = List.copyOf(remaining);
    }

    private static boolean samePair(String rule, String id, String mask) {
        String[] fields = rule.split("\\|", -1);
        return fields[0].trim().equals(id) && fields[1].trim().equalsIgnoreCase(mask);
    }

}
