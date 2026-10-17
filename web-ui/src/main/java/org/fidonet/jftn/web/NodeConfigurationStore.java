package org.fidonet.jftn.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.NodeConfiguration;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.nodelist.HttpDirectoryDownload;
import org.fidonet.types.Link;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Replaces configuration atomically and refuses to overwrite an external edit. */
public final class NodeConfigurationStore {
    private final Path path;
    private byte[] original;
    private boolean originalExists;
    @FunctionalInterface
    public interface Publication {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface Application {
        void apply(JFtnConfig candidate, Publication publication) throws Exception;
    }

    private final Application application;

    public NodeConfigurationStore(Path path) throws IOException {
        this(path, (candidate, publication) -> publication.run());
    }

    public NodeConfigurationStore(Path path, Application application) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        this.application = application;
        original = Files.exists(path) ? Files.readAllBytes(path) : new byte[0];
        originalExists = Files.exists(path);
    }

    public Path directory() {
        return path.getParent();
    }

    public Path receivedArchivePath() throws Exception {
        if (original.length == 0) {
            return directory().resolve("mail/received");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(path.toString());
        return config.isPreserveReceivedFiles() ? config.getReceivedArchivePath() : null;
    }

    public Path catalogPath() throws Exception {
        if (original.length == 0) {
            return directory().resolve("database/nodelist/catalog");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(path.toString());
        return Path.of(config.getValue("NodeDirectoryPath", "database/nodelist/catalog"));
    }

    public NodeConfiguration current() throws Exception {
        NodeConfiguration result = new NodeConfiguration("",
                                                         new ArrayList<>(),
                                                         "JFtn",
                                                         "",
                                                         "",
                                                         "",
                                                         "",
                                                         24554,
                                                         "",
                                                         directory().resolve("mail").toString(),
                                                         HttpDirectoryDownload.DEFAULT_URL,
                                                         "",
                                                         "",
                                                         "CP866",
                                                         "http://localhost:9091");
        if (original.length == 0) {
            return result;
        }
        JFtnConfig config = new JFtnConfig();
        config.load(path.toString());
        result.setSystem(config.getValue("System", "JFtn"));
        result.setPreserveReceivedFiles(config.isPreserveReceivedFiles());
        result.setSysop(config.getValue("SysOp", ""));
        result.setLocation(config.getValue("Location", ""));
        result.setNodeListUrl(config.getValue("NodeListUrl", result.getNodeListUrl()));
        result.setCharset(config.getValue("NodeListCharset", "CP866"));
        result.setBrowserOrigin(config.getValue("RpcHttpOrigin", result.getBrowserOrigin()));
        result.setStorage(config.getValue("UiMailStorage", result.getStorage()));
        if (config.getValue("Link") != null) {
            Link link = new Link(config.getValue("Link"));
            result.setUplink(link.getAddr().toString());
            result.setHost(link.getHostAddress() == null ? "" : link.getHostAddress());
            result.setPort(link.getPort() == 0 ? 24554 : link.getPort());
            List<String> local = new ArrayList<>();
            link.getMyaddr().forEach(address -> local.add(address.toString()));
            if (!local.isEmpty()) {
                result.setAddress(local.remove(0));
                result.setAliases(local);
            }
        }
        return result;
    }

    public void validate(NodeConfiguration value) {
        if (value == null || !value.isSetPort() || value.getPort() < 1 || value.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be in 1..65535");
        }
        AccountDirectory.address(value.getAddress(), false);
        AccountDirectory.address(value.getUplink(), false);
        if (value.getAliases() == null || value.getAliases().size() > 16) {
            throw new IllegalArgumentException("At most 16 station aliases are allowed");
        }
        value.getAliases().forEach(address -> AccountDirectory.address(address, false));
        text(value.getSystem());
        text(value.getSysop());
        text(value.getLocation());
        text(value.getStorage());
        text(value.getNodeListUrl());
        text(value.getCharset());
        text(value.getBrowserOrigin());
        text(value.getLinkPassword());
        text(value.getHost());
        if (value.getSysop().isBlank() || value.getSystem().isBlank()
                || value.getStorage().isBlank() || !value.getHost().matches("[A-Za-z0-9.-]{1,253}")
                || value.getLinkPassword().contains(",")) {
            throw new IllegalArgumentException("System, sysop, storage and DNS/IPv4 uplink are required");
        }
        Charset.forName(value.getCharset());
        if (original.length == 0
                && (!StandardCharsets.US_ASCII.newEncoder().canEncode(value.getLinkPassword())
                        || value.getLinkPassword().length() > 8)) {
            throw new IllegalArgumentException("Initial link password also protects PKT: use at most 8 ASCII characters");
        }
        URI origin = URI.create(value.getBrowserOrigin());
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !origin.getPath().isEmpty()
                || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))) {
            throw new IllegalArgumentException("Browser origin must be an exact HTTP(S) origin");
        }
        if (!value.getNodeListUrl().isBlank()) {
            URI url = URI.create(value.getNodeListUrl());
            if (!List.of("http", "https").contains(url.getScheme()) || url.getHost() == null
                    || url.getUserInfo() != null || url.getFragment() != null) {
                throw new IllegalArgumentException("Invalid HTTP nodelist URL");
            }
        }
    }

    private static void text(String value) {
        if (value == null || value.length() > 4096
                || value.chars().anyMatch(c -> c < 32 || c == '#')) {
            throw new IllegalArgumentException("Configuration values cannot contain control characters or #");
        }
    }

    public synchronized void save(NodeConfiguration value, Path authPath) throws Exception {
        validate(value);
        Map<String, String> values = values(value, authPath);
        if (original.length > 0) {
            configuration().getPacketPassword(new Link(values.get("link")));
        }
        List<String> lines = new ArrayList<>();
        boolean firstLink = true;
        for (String line : new String(original, StandardCharsets.UTF_8).split("\\R")) {
            String key = line.split("=", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (key.equals("link") && !firstLink) {
                lines.add(line);
                continue;
            }
            if (key.equals("link")) {
                firstLink = false;
            }
            if (values.containsKey(key)) {
                lines.add(key + "=" + values.remove(key));
            } else if (!key.equals("rpcpassword")) {
                lines.add(line);
            }
        }
        values.forEach((key, entry) -> lines.add(key + "=" + entry));
        byte[] next = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
        write(next);
    }

    private void write(byte[] next) throws Exception {
        byte[] fragments = fragmentsFingerprint();
        JFtnConfig candidate = JFtnConfig.candidate(path, next);
        Files.createDirectories(directory());
        Path stage = Files.createTempFile(directory(), ".jftn-config-", ".tmp");
        try {
            restrict(stage);
            try (FileChannel channel = FileChannel.open(stage, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(next);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            byte[] actual = Files.exists(path) ? Files.readAllBytes(path) : new byte[0];
            if (!Arrays.equals(actual, original)) {
                throw new IllegalArgumentException("Configuration changed externally; reload before saving");
            }
            boolean[] published = {false};
            try {
                application.apply(candidate, () -> {
                    verifySources(original, originalExists, fragments);
                    Files.move(stage,
                               path,
                               StandardCopyOption.ATOMIC_MOVE,
                               StandardCopyOption.REPLACE_EXISTING);
                    published[0] = true;
                });
            } catch (Exception failure) {
                if (published[0])
                    restoreIfPublished(next, failure);
                throw failure;
            }
            original = next;
            originalExists = true;
        } finally {
            Files.deleteIfExists(stage);
        }
    }

    private void restoreIfPublished(byte[] next, Exception failure) {
        try {
            if (Files.exists(path) && Arrays.equals(Files.readAllBytes(path), next)) {
                if (!originalExists) {
                    Files.delete(path);
                    return;
                }
                Path restore = Files.createTempFile(directory(), ".jftn-rollback-", ".tmp");
                try {
                    restrict(restore);
                    Files.write(restore, original);
                    Files.move(restore,
                               path,
                               StandardCopyOption.ATOMIC_MOVE,
                               StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(restore);
                }
            }
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    public synchronized void reload() throws Exception {
        byte[] next = Files.readAllBytes(path);
        byte[] fragments = fragmentsFingerprint();
        application.apply(JFtnConfig.candidate(path, next), () -> {
            verifySources(next, true, fragments);
        });
        original = next;
        originalExists = true;
    }

    private void verifySources(byte[] expected, boolean exists, byte[] fragments) throws Exception {
        byte[] actual = Files.exists(path) ? Files.readAllBytes(path) : new byte[0];
        if (Files.exists(path) != exists || !Arrays.equals(expected, actual)
                || !Arrays.equals(fragments, fragmentsFingerprint())) {
            throw new IllegalArgumentException("Configuration changed while applying; reload and try again");
        }
    }

    private byte[] fragmentsFingerprint() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Path fragments = directory().resolve("conf.d");
        if (!Files.isDirectory(fragments))
            return digest.digest();
        try (Stream<Path> stream = Files.list(fragments)) {
            List<Path> files = stream.filter(file -> file.getFileName()
                                                         .toString()
                                                         .endsWith(".conf"))
                                     .sorted()
                                     .collect(Collectors.toList());
            for (Path fragment : files) {
                digest.update(fragment.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(MessageDigest.getInstance("SHA-256")
                                           .digest(Files.readAllBytes(fragment)));
            }
        }
        return digest.digest();
    }

    synchronized JFtnConfig configuration() throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(path.toString());
        return config;
    }

    synchronized void replaceOptions(Map<String, List<String>> replacements) throws Exception {
        checkFragments(replacements);
        List<String> lines = new ArrayList<>();
        Map<String, List<String>> remaining = new LinkedHashMap<>(replacements);
        for (String line : new String(original, StandardCharsets.UTF_8).split("\\R")) {
            String key = line.split("=", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!replacements.containsKey(key)) {
                lines.add(line);
                continue;
            }
            List<String> entries = remaining.remove(key);
            if (entries != null) {
                entries.forEach(entry -> lines.add(key + "=" + entry));
            }
        }
        remaining.forEach((key, entries) -> entries.forEach(entry -> lines.add(key + "=" + entry)));
        write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void checkFragments(Map<String, List<String>> replacements) throws IOException {
        Path fragments = directory().resolve("conf.d");
        if (!Files.isDirectory(fragments)) {
            return;
        }
        try (Stream<Path> files = Files.list(fragments)) {
            for (Path fragment : files.filter(file -> file.toString().endsWith(".conf"))
                                      .collect(Collectors.toList())) {
                checkFragment(fragment, replacements);
            }
        }
    }

    private void checkFragment(Path fragment, Map<String, List<String>> replacements)
            throws IOException {
        for (String line : Files.readAllLines(fragment, StandardCharsets.UTF_8)) {
            String key = line.split("=", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (replacements.containsKey(key)) {
                throw new IllegalArgumentException("Move " + key
                        + " from conf.d to jftn.conf before editing it in the UI");
            }
        }
    }

    private static void restrict(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private Map<String, String> values(NodeConfiguration value, Path authPath) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        String password = value.getLinkPassword();
        if (password.isEmpty() && original.length > 0) {
            JFtnConfig config = new JFtnConfig();
            config.load(path.toString());
            password = new Link(config.getValue("Link")).getPass();
        }
        if (password == null || password.isEmpty()) {
            password = "-";
        }
        List<String> addresses = new ArrayList<>();
        addresses.add(value.getAddress());
        addresses.addAll(value.getAliases());
        result.put("link",
                   value.getUplink()
                   + ","
                   + String.join(" ", addresses)
                   + ","
                   + password
                   + ","
                   + value.getHost()
                   + ":"
                   + value.getPort());
        result.put("system", value.getSystem());
        result.put("sysop", value.getSysop());
        result.put("location", value.getLocation());
        result.put("nodelisturl", value.getNodeListUrl());
        result.put("nodelistcharset", value.getCharset());
        result.put("rpchttporigin", value.getBrowserOrigin());
        result.put("uimailstorage", value.getStorage());
        if (value.isSetPreserveReceivedFiles() || original.length == 0) {
            result.put("preservereceivedfiles", Boolean.toString(value.isPreserveReceivedFiles()));
        }
        if (original.length == 0) {
            result.put("rpcenabled", "true");
            result.put("rpchttpenabled", "true");
            result.put("rpcauthpath", authPath.toAbsolutePath().toString());
            Path storage = Path.of(value.getStorage()).toAbsolutePath();
            result.put("inbound", storage.resolve("inbound").toString());
            result.put("outbound", storage.resolve("outbound").toString());
            result.put("tmp", storage.resolve("tmp").toString());
            result.put("echopath", storage.resolve("echo").toString());
            result.put("rpchttpbind", "127.0.0.1");
            result.put("rpchttpport", "9091");
            result.put("rpcregistrationenabled", "true");
            URI origin = URI.create(value.getBrowserOrigin());
            boolean passkeys = origin.getHost().equals("localhost")
                    || (origin.getScheme().equals("https") && !origin.getHost().matches("[0-9.]+")
                            && !origin.getHost().contains(":"));
            result.put("rpcpasskeysenabled", Boolean.toString(passkeys));
            if (passkeys) {
                result.put("rpcwebauthnrpid", origin.getHost());
                result.put("rpcwebauthnorigin", value.getBrowserOrigin());
            }
        }
        return result;
    }
}
