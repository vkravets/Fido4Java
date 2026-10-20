package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.RuntimeProfile;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.echobase.MailStorage;
import org.fidonet.fileecho.storage.FileAccess;
import org.fidonet.fileecho.storage.FileArea;
import org.fidonet.fileecho.storage.FileCreationPolicy;
import org.fidonet.fileecho.storage.FileEchoStorage;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.storage.RepositoryAccess;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Path;
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
    private FileEchoStorage repository;
    private final Map<String, FileArea> declared = new TreeMap<>();
    private final Map<String, FileCreationPolicy> creation = new TreeMap<>();
    private final Map<String, String> fileCreators = new TreeMap<>();

    FileEchoAreaService(JFtnConfig config, MailStorage base, ScriptState state) throws IOException {
        this.config = config;
        this.state = state;
        String source = config.getValue("FileEchoSource",
                                        RuntimeProfile.read(config).includesUiByDefault()
                                                ? "database"
                                                : "files");
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
                                     new FileArea(tag, autoDirectory(tag).toString(), "", true));
            }
        }
        if (!files) {
            if (!(base instanceof RepositoryAccess)) {
                throw new IOException("Database-managed FileEcho requires typed repository access");
            }
            repository = ((RepositoryAccess) base).repository(FileEchoStorage.class);
            repository.seedAreas(declared.values());
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
            FileArea area = new FileArea(tag,
                                         Path.of(fields[1]).toAbsolutePath().normalize().toString(),
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
            if (creation.put(peer, new FileCreationPolicy(peer, fields[1], fields[2])) != null) {
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

    List<FileArea> list() throws IOException {
        return files ? List.copyOf(declared.values()) : repository.areas();
    }

    FileArea area(String tag) throws IOException {
        return files ? declared.get(tag) : repository.area(tag);
    }

    List<FileAccess> access() throws IOException {
        return files ? List.of() : repository.access();
    }

    List<FileCreationPolicy> policies() throws IOException {
        return files ? List.copyOf(creation.values()) : repository.policies();
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
        repository.saveArea(new FileArea(tag, path.toString(), description, enabled));
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
        for (FileAccess rule : repository.access(peer.toString())) {
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
        repository.subscribe(tag, peer.toString(), enabled);
    }

    void saveAccess(String name, String peer, String level) throws IOException {
        writable();
        String tag = tag(name);
        String address = new FTNAddr(peer).toString();
        if (config.getLink(new FTNAddr(peer)) == null || area(tag) == null
                || !Set.of("NONE", "READ", "WRITE", "BOTH").contains(level)) {
            throw new IllegalArgumentException("Unknown area/link or invalid access");
        }
        repository.saveAccess(tag, address, level);
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
        repository.savePolicy(address, masks, level);
    }

    private boolean matches(FileCreationPolicy policy, String tag) {
        return policy != null && Arrays.stream(policy.masks.split(","))
                                       .anyMatch(mask -> RobotAccessRule.matches(mask.trim(), tag));
    }

    boolean canReceive(String tag, FTNAddr peer) throws IOException {
        FileArea area = area(tag);
        if (area != null) {
            if (!area.enabled) {
                return false;
            }
            if (files) {
                return !fileCreators.containsKey(tag)
                        || fileCreators.get(tag).equals(peer.toString());
            }
            FileAccess rule = repository.access(tag, peer.toString());
            return rule != null && Set.of("WRITE", "BOTH").contains(rule.access);
        }
        FileCreationPolicy policy = files
                ? creation.get(peer.toString())
                : repository.policy(peer.toString());
        return matches(policy, tag);
    }

    FileArea createAfterValidation(String name, FTNAddr peer) throws IOException {
        String tag = tag(name);
        if (!canReceive(tag, peer)) {
            throw new IOException("File echo import is not permitted");
        }
        FileArea existing = area(tag);
        if (existing != null) {
            return existing;
        }
        Path directory = autoDirectory(tag);
        FileArea created = new FileArea(tag, directory.toString(), "", true);
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
        FileCreationPolicy policy = repository.policy(peer.toString());
        if (!matches(policy, tag)) {
            throw new IOException("File creation policy changed");
        }
        FileArea result = repository.createWithGrant(created, policy);
        if (!canReceive(tag, peer)) {
            throw new IOException("File echo import is not permitted");
        }
        return result;
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

}
