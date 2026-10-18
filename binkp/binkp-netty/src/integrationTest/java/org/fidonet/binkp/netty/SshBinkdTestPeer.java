package org.fidonet.binkp.netty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Owns one isolated SSH server, generated client key, trusted host key and binkd files. */
final class SshBinkdTestPeer implements AutoCloseable {
    private static final String IMAGE = "localhost/jftn-ssh-binkd-interop:local";
    private final Path root;
    private final String engine = System.getProperty("ssh.container.engine", "docker");
    private final String host = System.getProperty("ssh.container.host", "127.0.0.1");
    private String container;
    private int port;

    SshBinkdTestPeer(Path root) {
        this.root = root;
    }

    void start(byte[] outbound) throws Exception {
        Files.createDirectories(root);
        Path dockerfile = root.resolve("Dockerfile");
        try (InputStream source = getClass().getResourceAsStream("/ssh-binkd/Dockerfile")) {
            Files.copy(source, dockerfile);
        }
        ContainerCommand.run(List.of(engine, "info"), Duration.ofSeconds(20));
        cli(Duration.ofMinutes(5), "build", "-t", IMAGE, root.toString());
        container = cli(Duration.ofSeconds(30),
                        "run",
                        "-d",
                        "--rm",
                        "-p",
                        "22",
                        IMAGE).lines()
                              .filter(line -> line.matches("[0-9a-f]{12,64}"))
                              .findFirst()
                              .orElseThrow();
        String endpoint = cli(Duration.ofSeconds(10),
                              "port",
                              container,
                              "22/tcp").lines()
                                       .filter(line -> line.startsWith("0.0.0.0:")
                                               || line.startsWith("127.0.0.1:"))
                                       .findFirst()
                                       .orElseThrow();
        port = Integer.parseInt(endpoint.substring(endpoint.lastIndexOf(':') + 1));
        ContainerCommand.run(List.of(System.getProperty("ssh.keygen", "ssh-keygen"),
                                     "-q",
                                     "-t",
                                     "ed25519",
                                     "-N",
                                     "",
                                     "-f",
                                     root.resolve("identity").toString()),
                             Duration.ofSeconds(10));
        copyIn(root.resolve("identity.pub"), "/root/.ssh/authorized_keys");
        cli(Duration.ofSeconds(10),
            "exec",
            container,
            "chmod",
            "600",
            "/root/.ssh/authorized_keys");
        Path data = root.resolve("REMOTE.PKT");
        Files.write(data, outbound);
        copyIn(data, "/data/outbox/REMOTE.PKT");
        Path config = root.resolve("binkd.cfg");
        Files.writeString(config,
                          "sysname SSH-test\nsysop Test\nlocation Test\nnodeinfo BINKP\n"
                                  + "domain fidonet.org /data/outbound 1\naddress 1:500/101@fidonet.org\n"
                                  + "inbound /data/inbound\ninbound-nonsecure /data/nonsecure\n"
                                  + "temp-inbound /data/partial\nprescan\nsend-if-pwd\nlog /data/binkd.log\n"
                                  + "conlog 4\nnode 1:500/100@fidonet.org -nohc 127.0.0.1 ssh-test-secret i /data/outbox /data/inbound\n");
        copyIn(config, "/data/binkd.cfg");
        trustHost();
        Files.writeString(root.resolve("ssh_config"),
                          "Host peer\n HostName " + host + "\n Port " + port
                                  + "\n User root\n IdentitiesOnly yes\n IdentityFile \""
                                  + path("identity") + "\"\n UserKnownHostsFile \""
                                  + path("known_hosts")
                                  + "\"\n GlobalKnownHostsFile none\n ConnectionAttempts 5\n ConnectTimeout 3\n");
    }

    private String path(String name) {
        return root.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    }

    void trustHost() throws Exception {
        String key = cli(Duration.ofSeconds(10),
                         "exec",
                         container,
                         "cat",
                         "/etc/ssh/ssh_host_ed25519_key.pub");
        Files.writeString(root.resolve("known_hosts"), "[" + host + "]:" + port + " " + key + "\n");
    }

    List<String> command() {
        return List.of(System.getProperty("ssh.executable", "ssh"),
                       "-T",
                       "-o",
                       "BatchMode=yes",
                       "-o",
                       "StrictHostKeyChecking=yes",
                       "-F",
                       path("ssh_config"),
                       "peer",
                       "exec /usr/sbin/binkd -i /data/binkd.cfg");
    }

    void forgetHost() throws Exception {
        Files.writeString(root.resolve("known_hosts"), "");
    }

    void denyAuthentication() throws Exception {
        Path empty = root.resolve("empty-authorized-keys");
        Files.writeString(empty, "");
        copyIn(empty, "/root/.ssh/authorized_keys");
        cli(Duration.ofSeconds(10),
            "exec",
            container,
            "chmod",
            "600",
            "/root/.ssh/authorized_keys");
    }

    byte[] received() throws Exception {
        Path received = root.resolve("received.pkt");
        cli(Duration.ofSeconds(10),
            "cp",
            container + ":/data/inbound/LOCAL.PKT",
            received.toString());
        return Files.readAllBytes(received);
    }

    void saveLogs(Path artifacts) throws Exception {
        Files.writeString(artifacts.resolve("sshd.log"),
                          cli(Duration.ofSeconds(10), "logs", container));
        try {
            cli(Duration.ofSeconds(10),
                "cp",
                container + ":/data/binkd.log",
                artifacts.resolve("binkd.log").toString());
        } catch (Exception failure) {
            Files.writeString(artifacts.resolve("binkd-log-error.txt"), failure.toString());
        }
    }

    private void copyIn(Path source, String destination) throws Exception {
        cli(Duration.ofSeconds(10), "cp", source.toString(), container + ":" + destination);
    }

    private String cli(Duration timeout, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(engine);
        command.addAll(List.of(arguments));
        return ContainerCommand.run(command, timeout);
    }

    @Override
    public void close() throws Exception {
        if (container != null) {
            cli(Duration.ofSeconds(15), "rm", "-f", container);
        }
    }
}
