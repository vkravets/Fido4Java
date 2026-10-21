package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.plugins.ui.SettingsExtension;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
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
                            fields[2].trim()));
        }
        List<Field> fields = List.of(new Field("source", "Source", "select", service.sourceIds()),
                                     new Field("mask", "Filename mask", "text", List.of()),
                                     new Field("mode",
                                               "CRC mode",
                                               "select",
                                               List.of("standard", "new-point-keeper")));
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
                                               "standard",
                                               "Стандартний XMODEM",
                                               "new-point-keeper",
                                               "New Point Keeper (XMODEM / ARC)",
                                               "Legacy CRC exceptions",
                                               "Старі винятки перевірки CRC");
        return new View(id(),
                        "Node directory",
                        "conf.d/30-nodelist.conf",
                        sections,
                        Map.of("uk",
                               ukrainian,
                               "en",
                               Map.of("standard",
                                      "Standard XMODEM",
                                      "new-point-keeper",
                                      "New Point Keeper (XMODEM / ARC)")));
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
        String mode = values.get("mode");
        if (!Set.of("standard", "new-point-keeper").contains(mode)) {
            throw new IllegalArgumentException("Unknown directory CRC mode");
        }
        String pair = id + "|" + mask;
        // Always validate the source and mask, including removal/standard commands.
        new DirectoryChecksumExceptions(DirectoryCrcConfiguration.parse(DirectoryCrcConfiguration.COMPATIBILITY
                + "=" + pair + "|new-point-keeper\n"), Set.copyOf(service.sourceIds()));
        List<String> next = new ArrayList<>(compatibility);
        next.removeIf(rule -> samePair(rule, id, mask));
        if (mode.equals("new-point-keeper")) {
            next.add(pair + "|new-point-keeper");
        }
        List<String> remaining = new ArrayList<>(ignored);
        remaining.removeIf(rule -> samePair(rule, id, mask));
        if (mode.equals("standard")) {
            requireStrictScope(next, id, mask);
            requireStrictScope(remaining, id, mask);
        }
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

    private static void requireStrictScope(List<String> rules, String id, String mask) {
        for (String rule : rules) {
            String[] fields = rule.split("\\|", -1);
            if (fields[0].trim().equals(id) && overlaps(fields[1].trim(), mask)) {
                throw new IllegalArgumentException("Another CRC rule overlaps this mask; resolve overlapping rules before selecting Standard XMODEM");
            }
        }
    }

    /** Whether two validated '*'/'?' filename globs can select the same name. */
    static boolean overlaps(String first, String second) {
        String a = first.toUpperCase(Locale.ROOT);
        String b = second.toUpperCase(Locale.ROOT);
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        pending.add(new int[]{0, 0});
        while (!pending.isEmpty()) {
            int[] state = pending.remove();
            int i = state[0];
            int j = state[1];
            if (!seen.add(i * (b.length() + 1) + j)) {
                continue;
            }
            if (i == a.length() && j == b.length()) {
                return true;
            }
            char x = i < a.length() ? a.charAt(i) : 0;
            char y = j < b.length() ? b.charAt(j) : 0;
            if (x == '*') {
                pending.add(new int[]{i + 1, j});
            }
            if (y == '*') {
                pending.add(new int[]{i, j + 1});
            }
            if (x != 0 && y != 0 && (x == '*' || y == '*' || x == '?' || y == '?' || x == y)) {
                pending.add(new int[]{x == '*' ? i : i + 1, y == '*' ? j : j + 1});
            }
        }
        return false;
    }
}
