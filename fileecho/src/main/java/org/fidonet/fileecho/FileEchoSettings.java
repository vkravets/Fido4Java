package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Owns the extension schema and validation; the UI knows only declarative fields and rows. */
final class FileEchoSettings implements SettingsExtension {
    private final MailAutomationRuntime api;
    private final FileEchoAreaService store;
    private final JFtnConfig config;

    FileEchoSettings(MailAutomationRuntime api, FileEchoAreaService store) {
        this.api = api;
        this.store = store;
        config = (JFtnConfig) api.getConfig();
    }

    @Override
    public String id() {
        return "fileecho";
    }

    private Field text(String name, String label) {
        return new Field(name, label, "text", List.of());
    }

    private Field choice(String name, String label, List<String> values) {
        return new Field(name, label, "select", values);
    }

    private Field peer() {
        return choice("peer",
                      "Link",
                      config.getConfiguredLinks()
                            .stream()
                            .map(link -> link.getAddr().toString())
                            .collect(Collectors.toList()));
    }

    @Override
    public View describe() throws IOException {
        List<Section> sections = new ArrayList<>();
        List<Map<String, String>> areas = new ArrayList<>();
        for (FileAreaRecord area : store.list()) {
            areas.add(Map.of("name",
                             area.name,
                             "directory",
                             area.directory,
                             "description",
                             area.description,
                             "enabled",
                             Boolean.toString(area.enabled)));
        }
        sections.add(new Section("areas",
                                 "File areas",
                                 List.of(text("name", "Area"),
                                         text("directory", "Directory"),
                                         text("description", "Description"),
                                         choice("enabled", "Enabled", List.of("true", "false"))),
                                 areas,
                                 !store.isFiles()));
        List<Map<String, String>> access = new ArrayList<>();
        for (FileAccessRecord rule : store.access()) {
            access.add(Map.of("name", rule.area, "peer", rule.peer, "access", rule.access));
        }
        List<String> names = areas.stream()
                                  .map(area -> area.get("name"))
                                  .collect(Collectors.toList());
        sections.add(new Section("access",
                                 "File area access",
                                 List.of(choice("name", "Area", names),
                                         peer(),
                                         choice("access",
                                                "Access",
                                                List.of("READ", "WRITE", "BOTH", "NONE"))),
                                 access,
                                 !store.isFiles()));
        List<Map<String, String>> policies = new ArrayList<>();
        for (FileCreationRecord policy : store.policies()) {
            policies.add(Map.of("peer",
                                policy.peer,
                                "masks",
                                policy.masks,
                                "access",
                                policy.access));
        }
        sections.add(new Section("creation",
                                 "Automatic file area creation",
                                 List.of(peer(),
                                         text("masks", "Area masks"),
                                         choice("access",
                                                "Access",
                                                List.of("WRITE", "BOTH", "NONE"))),
                                 policies,
                                 !store.isFiles()));
        sections.add(robotSection());
        return new View(id(),
                        "File echoes",
                        store.isFiles() ? "files" : "database",
                        sections,
                        Map.of("en",
                               Map.of("true",
                                      "Yes",
                                      "false",
                                      "No",
                                      "READ",
                                      "Receives from us",
                                      "WRITE",
                                      "Sends to us",
                                      "BOTH",
                                      "Both directions",
                                      "NONE",
                                      "No access"),
                               "uk",
                               Map.ofEntries(Map.entry("File echoes", "Файлові конференції"),
                                             Map.entry("File areas", "Файлові області"),
                                             Map.entry("File area access",
                                                       "Доступ до файлових областей"),
                                             Map.entry("Automatic file area creation",
                                                       "Автоматичне створення файлових областей"),
                                             Map.entry("FileFix and TIC access",
                                                       "Доступ до FileFix і TIC"),
                                             Map.entry("Area", "Область"),
                                             Map.entry("Directory", "Каталог"),
                                             Map.entry("Description", "Опис"),
                                             Map.entry("Enabled", "Увімкнено"),
                                             Map.entry("Link", "Лінк"),
                                             Map.entry("Access", "Доступ"),
                                             Map.entry("Area masks", "Маски областей"),
                                             Map.entry("Service", "Служба"),
                                             Map.entry("Password", "Пароль"),
                                             Map.entry("true", "Так"),
                                             Map.entry("false", "Ні"),
                                             Map.entry("READ", "Отримує від нас"),
                                             Map.entry("WRITE", "Надсилає нам"),
                                             Map.entry("BOTH", "Обидва напрямки"),
                                             Map.entry("NONE", "Без доступу"))));
    }

    private Section robotSection() {
        List<Map<String, String>> rows = new ArrayList<>();
        if (api.getRobotAccess().getSource().equals("database")) {
            for (RobotService service : List.of(RobotService.FileFix, RobotService.Tic)) {
                for (RobotAccessRule rule : api.getDatabase().getRobotRules().list(service)) {
                    rows.add(Map.of("service",
                                    service.name(),
                                    "peer",
                                    rule.getAddress(),
                                    "masks",
                                    rule.getMasks(),
                                    "enabled",
                                    Boolean.toString(rule.isEnabled()),
                                    "password",
                                    ""));
                }
            }
        }
        return new Section("robots",
                           "FileFix and TIC access",
                           List.of(choice("service", "Service", List.of("FileFix", "Tic")),
                                   peer(),
                                   new Field("password", "Password", "password", List.of()),
                                   text("masks", "Area masks"),
                                   choice("enabled", "Enabled", List.of("true", "false"))),
                           rows,
                           api.getRobotAccess().getSource().equals("database"));
    }

    private String value(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing extension field: " + key);
        }
        return value;
    }

    private boolean enabled(Map<String, String> values) {
        String value = value(values, "enabled");
        if (!Set.of("true", "false").contains(value)) {
            throw new IllegalArgumentException("Enabled must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public void apply(String section, Map<String, String> values) throws IOException {
        Section schema = describe().sections.stream()
                                            .filter(item -> item.id.equals(section))
                                            .findFirst()
                                            .orElseThrow(() -> new IllegalArgumentException("Unknown extension section"));
        if (!schema.writable) {
            throw new IllegalArgumentException("This section is configured through files");
        }
        Set<String> expected = schema.fields.stream()
                                            .map(field -> field.name)
                                            .collect(Collectors.toSet());
        if (!values.keySet().equals(expected)) {
            throw new IllegalArgumentException("Unexpected extension fields");
        }
        switch (section) {
            case "areas" :
                store.saveArea(value(values, "name"),
                               value(values, "directory"),
                               value(values, "description"),
                               enabled(values));
                break;
            case "access" :
                store.saveAccess(value(values, "name"),
                                 value(values, "peer"),
                                 value(values, "access"));
                break;
            case "creation" :
                store.savePolicy(value(values, "peer"),
                                 value(values, "masks"),
                                 value(values, "access"));
                break;
            case "robots" :
                saveRobot(values);
                break;
            default :
                throw new IllegalArgumentException("Unknown extension section");
        }
    }

    private void saveRobot(Map<String, String> values) {
        String service = value(values, "service");
        if (!Set.of("FileFix", "Tic").contains(service)) {
            throw new IllegalArgumentException("Unsupported file echo service");
        }
        String peer = value(values, "peer");
        if (config.getLink(new FTNAddr(peer)) == null) {
            throw new IllegalArgumentException("Unknown link");
        }
        RobotService kind = RobotService.valueOf(service);
        String address = new FTNAddr(peer).toString();
        RobotAccessRule previous = api.getDatabase()
                                      .getRobotRules()
                                      .list(kind)
                                      .stream()
                                      .filter(rule -> rule.getAddress().equals(address))
                                      .findFirst()
                                      .orElse(null);
        String password = value(values, "password");
        String masks = value(values, "masks");
        boolean active = enabled(values);
        RobotAccessRule next = password.isEmpty() && previous != null
                ? previous.withPolicy(masks, "", active)
                : RobotAccessRule.create(kind, address, password, masks, "", active);
        api.getDatabase().getRobotRules().save(next);
    }
}
