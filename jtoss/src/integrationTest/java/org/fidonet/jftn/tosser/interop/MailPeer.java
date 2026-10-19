package org.fidonet.jftn.tosser.interop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Runs production Java and real Husky/binkd processes against the same Linux paths. */
final class MailPeer implements AutoCloseable {
    static final String ROOT = "/data/scenario";
    private final MailPeerCommand commands;
    private final String network;
    private final String alias;
    private final Path artifacts;
    private String id;

    MailPeer(MailPeerCommand commands, String network, String alias, Path artifacts) {
        this.commands = commands;
        this.network = network;
        this.alias = alias;
        this.artifacts = artifacts;
    }

    void start() throws Exception {
        String image = System.getProperty("mailInterop.image", "jftn-hpt-interop:local");
        id = commands.execute("create", "--network", network, "--network-alias", alias, image);
        if (!id.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid container ID");
        }
        commands.execute("start", id);
        commands.execute("cp",
                         Path.of(System.getProperty("hpt.workerFiles")).toAbsolutePath() + "/.",
                         id + ":/opt/jftn/lib/");
        exec("mkdir",
             "-p",
             ROOT,
             ROOT + "/inbound",
             ROOT + "/nonsecure",
             ROOT + "/partial",
             ROOT + "/bso",
             ROOT + "/logs");
    }

    String exec(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("exec", id));
        command.addAll(Arrays.asList(arguments));
        return commands.execute(command.toArray(new String[0]));
    }

    void worker(String type, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("timeout",
                                                       "60s",
                                                       "java",
                                                       "-Xmx256m",
                                                       "-cp",
                                                       "/opt/jftn/lib/*",
                                                       type,
                                                       ROOT));
        command.addAll(Arrays.asList(arguments));
        exec(command.toArray(new String[0]));
    }

    void configuration(String name, String resource, String local, String remote, String host)
            throws Exception {
        String text;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            text = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
        text = text.replace("@ROOT@", ROOT)
                   .replace("@LOCAL@", local)
                   .replace("@REMOTE@", remote)
                   .replace("@HOST@", host);
        Path temporary = Files.createTempFile(artifacts, alias + "-", "-" + name);
        Files.writeString(temporary, text);
        commands.execute("cp", temporary.toAbsolutePath().toString(), id + ":" + ROOT + "/" + name);
    }

    void serve() throws Exception {
        commands.execute("exec", "-d", id, "binkd", "-s", "-m", ROOT + "/binkd.cfg");
        exec("timeout",
             "15s",
             "sh",
             "-c",
             "until grep -qi ':5FEA .* 0A ' /proc/net/tcp /proc/net/tcp6; do sleep 0.1; done");
    }

    void call(String address) throws Exception {
        exec("timeout",
             "40s",
             "binkd",
             "-p",
             "-m",
             "-P",
             address + "@fidonet",
             ROOT + "/binkd.cfg");
    }

    @Override
    public void close() throws Exception {
        if (id == null || !id.matches("[0-9a-f]{64}")) {
            return;
        }
        try {
            exec("tar", "-czf", "/data/diagnostics.tar.gz", "-C", "/data", "scenario");
            commands.execute("cp",
                             id + ":/data/diagnostics.tar.gz",
                             artifacts.resolve(alias + ".tar.gz").toAbsolutePath().toString());
        } finally {
            commands.execute("rm", "--force", id);
        }
    }
}
