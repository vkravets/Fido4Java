package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** File area operations and access/creation policy over SQL or explicit file-managed state. */
final class FileEchoAreaService {
    private final JFtnConfig config;
    private final ScriptState state;
    private final boolean files;
    private FileEchoRepository repository;
    private final Map<String, FileAreaRecord> declared = new TreeMap<>();
    private final Map<String, FileCreationRecord> creation = new TreeMap<>();
    private final Map<String, String> fileCreators = new TreeMap<>();

    FileEchoAreaService(JFtnConfig config, MailDatabaseFacade base, ScriptState state)
            throws IOException {
        this.config = config;
        this.state = state;
        String source = config.getValue("FileEchoSource", "database");
        if (!source.equals("files") && !source.equals("database")) {
            throw new IllegalArgumentException("FileEchoSource must be database or files");
        }
        files = source.equals("files");
        readDeclarations();
        if (files) {
            String saved = state.get("fileecho-created-areas");
            for (String line : saved == null || saved.isEmpty()
                    ? List.<String>of()
                    : Arrays.asList(saved.split("\n"))) {
                String[] fields = line.split("\\|", -1);
                if (fields.length != 2) {
                    throw new IOException("Invalid fileecho creation state");
                }
                String tag = tag(fields[0]);
                fileCreators.put(tag, new FTNAddr(fields[1]).toString());
                declared.putIfAbsent(tag,
                                     new FileAreaRecord(tag,
                                                        autoDirectory(tag).toString(),
                                                        "",
                                                        true));
            }
        }
        if (!files) {
            repository = new FileEchoRepository(base);
            sql(() -> {
                // Configured initial areas are insert-only. UI edits/disabled records survive restart.
                for (FileAreaRecord area : declared.values()) {
                    if (!repository.areas.idExists(area.name)) {
                        repository.areas.create(area);
                    }
                }
                return null;
            });
        }
    }

    private List<String> values(String key) {
        List<String> values = config.getValuesAsList(key);
        return values == null ? List.of() : values;
    }

    private void readDeclarations() {
        for (String value : values("FileArea")) {
            String[] fields = value.split("\\|", -1);
            if (fields.length < 2 || fields.length > 3 || fields[1].isBlank()) {
                throw new IllegalArgumentException("FileArea=tag|directory[|description]");
            }
            String tag = tag(fields[0]);
            FileAreaRecord area = new FileAreaRecord(tag,
                                                     Path.of(fields[1])
                                                         .toAbsolutePath()
                                                         .normalize()
                                                         .toString(),
                                                     fields.length == 3 ? fields[2] : "",
                                                     true);
            if (declared.put(tag, area) != null) {
                throw new IllegalArgumentException("Duplicate FileArea " + tag);
            }
        }
        for (String value : values("AutoCreateFileFrom")) {
            String[] fields = value.split("\\|", -1);
            if (fields.length != 3 || config.getLink(new FTNAddr(fields[0])) == null
                    || fields[1].isBlank() || !Set.of("WRITE", "BOTH").contains(fields[2])) {
                throw new IllegalArgumentException("AutoCreateFileFrom=link|masks|WRITE or BOTH");
            }
            String peer = new FTNAddr(fields[0]).toString();
            if (creation.put(peer, new FileCreationRecord(peer, fields[1], fields[2])) != null) {
                throw new IllegalArgumentException("Duplicate AutoCreateFileFrom link");
            }
        }
        if (!files && !creation.isEmpty()) {
            throw new IllegalArgumentException("AutoCreateFileFrom requires FileEchoSource=files; use UI for database policies");
        }
    }

    static String tag(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,59}")) {
            throw new IllegalArgumentException("Invalid file area tag");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    boolean isFiles() {
        return files;
    }

    List<FileAreaRecord> list() throws IOException {
        return files ? List.copyOf(declared.values()) : sql(() -> repository.areas.queryForAll());
    }

    FileAreaRecord area(String tag) throws IOException {
        return files ? declared.get(tag) : sql(() -> repository.areas.queryForId(tag));
    }

    List<FileAccessRecord> access() throws IOException {
        return files ? List.of() : sql(() -> repository.access.queryForAll());
    }

    List<FileCreationRecord> policies() throws IOException {
        return files
                ? List.copyOf(creation.values())
                : sql(() -> repository.policies.queryForAll());
    }

    void saveArea(String name, String directory, String description, boolean enabled)
            throws IOException {
        writable();
        String tag = tag(name);
        if (directory == null || directory.isBlank() || directory.length() > 4096
                || description == null || description.length() > 1024) {
            throw new IllegalArgumentException("File area directory and description are invalid");
        }
        Path path = Path.of(directory).toAbsolutePath().normalize();
        sql(() -> repository.areas.createOrUpdate(new FileAreaRecord(tag,
                                                                     path.toString(),
                                                                     description,
                                                                     enabled)));
    }

    Set<String> subscriptions(FTNAddr peer) throws IOException {
        Set<String> result = new TreeSet<>();
        if (files) {
            String saved = state.get("filefix-subscriptions:" + peer);
            if (saved != null && !saved.isEmpty()) {
                result.addAll(Arrays.asList(saved.split("\n")));
            }
            return result;
        }
        for (FileAccessRecord rule : sql(() -> repository.access.queryForEq("peer",
                                                                            peer.toString()))) {
            if (Set.of("READ", "BOTH").contains(rule.access)) {
                result.add(rule.area);
            }
        }
        return result;
    }

    void subscribe(FTNAddr peer, String tag, boolean enabled) throws IOException {
        if (files) {
            Set<String> linked = subscriptions(peer);
            if (enabled) {
                linked.add(tag);
            } else {
                linked.remove(tag);
            }
            state.put("filefix-subscriptions:" + peer, String.join("\n", linked));
            return;
        }
        sql(() -> repository.transaction(() -> {
            FileAccessRecord previous = repository.access.queryForId(tag + ":" + peer);
            if (!enabled) {
                repository.access.deleteById(tag + ":" + peer);
            } else if (previous == null) {
                repository.access.create(new FileAccessRecord(tag, peer.toString(), "READ"));
            } else if (previous.access.equals("WRITE")) {
                previous.access = "BOTH";
                repository.access.update(previous);
            }
            return null;
        }));
    }

    void saveAccess(String name, String peer, String level) throws IOException {
        writable();
        String tag = tag(name);
        String address = new FTNAddr(peer).toString();
        if (config.getLink(new FTNAddr(peer)) == null || area(tag) == null
                || !Set.of("NONE", "READ", "WRITE", "BOTH").contains(level)) {
            throw new IllegalArgumentException("Unknown area/link or invalid access");
        }
        sql(() -> {
            if (level.equals("NONE")) {
                repository.access.deleteById(tag + ":" + address);
            } else {
                repository.access.createOrUpdate(new FileAccessRecord(tag, address, level));
            }
            return null;
        });
    }

    void savePolicy(String peer, String masks, String level) throws IOException {
        writable();
        String address = new FTNAddr(peer).toString();
        if (config.getLink(new FTNAddr(peer)) == null || masks == null || masks.isBlank()
                || masks.length() > 1024 || !Set.of("NONE", "WRITE", "BOTH").contains(level)) {
            throw new IllegalArgumentException("Invalid file area creation policy");
        }
        if (Arrays.stream(masks.split(",", -1)).anyMatch(String::isBlank)
                || masks.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid file area masks");
        }
        sql(() -> {
            if (level.equals("NONE")) {
                repository.policies.deleteById(address);
            } else {
                repository.policies.createOrUpdate(new FileCreationRecord(address, masks, level));
            }
            return null;
        });
    }

    private boolean matches(FileCreationRecord policy, String tag) {
        return policy != null && Arrays.stream(policy.masks.split(","))
                                       .anyMatch(mask -> RobotAccessRule.matches(mask.trim(), tag));
    }

    boolean canReceive(String tag, FTNAddr peer) throws IOException {
        FileAreaRecord area = area(tag);
        if (area != null) {
            if (!area.enabled) {
                return false;
            }
            if (files) {
                return !fileCreators.containsKey(tag)
                        || fileCreators.get(tag).equals(peer.toString());
            }
            FileAccessRecord rule = sql(() -> repository.access.queryForId(tag + ":" + peer));
            return rule != null && Set.of("WRITE", "BOTH").contains(rule.access);
        }
        FileCreationRecord policy = files
                ? creation.get(peer.toString())
                : sql(() -> repository.policies.queryForId(peer.toString()));
        return matches(policy, tag);
    }

    FileAreaRecord createAfterValidation(String name, FTNAddr peer) throws IOException {
        String tag = tag(name);
        if (!canReceive(tag, peer)) {
            throw new IOException("File echo import is not permitted");
        }
        FileAreaRecord existing = area(tag);
        if (existing != null) {
            return existing;
        }
        Path directory = autoDirectory(tag);
        FileAreaRecord created = new FileAreaRecord(tag, directory.toString(), "", true);
        if (files) {
            Map<String, String> next = new TreeMap<>(fileCreators);
            next.put(tag, peer.toString());
            List<String> rows = new ArrayList<>();
            next.forEach((area, creator) -> rows.add(area + "|" + creator));
            state.put("fileecho-created-areas", String.join("\n", rows));
            fileCreators.putAll(next);
            declared.put(tag, created);
            return created;
        }
        return sql(() -> repository.transaction(() -> {
            FileAreaRecord current = repository.areas.queryForId(tag);
            if (current != null) {
                return current;
            }
            FileCreationRecord policy = repository.policies.queryForId(peer.toString());
            if (!matches(policy, tag)) {
                throw new SQLException("File creation policy changed");
            }
            repository.areas.create(created);
            repository.access.create(new FileAccessRecord(tag, peer.toString(), policy.access));
            return created;
        }));
    }

    private Path autoDirectory(String tag) throws IOException {
        Path root = Path.of(config.getValue("FileEchoRoot", "./fileecho"))
                        .toAbsolutePath()
                        .normalize();
        Path directory = root.resolve(tag).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IOException("Invalid auto-created file area path");
        }
        return directory;
    }

    private void writable() {
        if (files) {
            throw new IllegalStateException("File echo configuration is managed by files");
        }
    }

    private <T> T sql(FileEchoRepository.Work<T> work) throws IOException {
        return repository.execute(work);
    }
}
