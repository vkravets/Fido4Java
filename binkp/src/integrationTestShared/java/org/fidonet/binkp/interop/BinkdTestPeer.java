package org.fidonet.binkp.interop;

import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** Owns the external process, container files and container diagnostics. */
final class BinkdTestPeer implements AutoCloseable {
    private static final int BINKP_PORT = 24554;
    private final ExchangeFiles files;
    private final List<String> console = new CopyOnWriteArrayList<>();
    private final GenericContainer<?> container;
    private ExecutorService caller;
    private Future<Container.ExecResult> clientProcess;

    BinkdTestPeer(ExchangeFiles files) {
        this.files = files;
        // Image building and Docker access are deferred until start().
        container = new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("Dockerfile", "binkd/Dockerfile"))
                .withCopyToContainer(Transferable.of(files.toJftn, 0644), "/data/outbox/" + ExchangeFiles.FROM_BINKD)
                .withStartupTimeout(Duration.ofSeconds(90))
                .withLogConsumer(frame -> console.add(frame.getUtf8String()));
    }

    void startServer(Path artifacts) throws Exception {
        copyConfiguration("-");
        container.withExposedPorts(BINKP_PORT).waitingFor(Wait.forListeningPort()).start();
        saveEndpoint(artifacts, host() + ":" + port());
    }

    void startClient(int jftnPort, Path artifacts) throws Exception {
        Testcontainers.exposeHostPorts(jftnPort);
        String endpoint = "host.testcontainers.internal:" + jftnPort;
        copyConfiguration(endpoint);
        container.withAccessToHost(true)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh", "-c"))
                .withCommand(new String[] {"echo binkd-client-ready; exec sleep infinity"})
                .waitingFor(Wait.forLogMessage(".*binkd-client-ready.*\\n", 1)).start();
        saveEndpoint(artifacts, endpoint);
        caller = Executors.newSingleThreadExecutor();
        clientProcess = caller.submit(() -> container.execInContainer("/usr/sbin/binkd", "-p", "-m",
                "-P", "1:500/101@fidonet.org", "/data/binkd.cfg"));
    }

    String host() {
        return container.getHost();
    }

    int port() {
        return container.getMappedPort(BINKP_PORT);
    }

    void assertClientFinished(Path artifacts) throws Exception {
        if (clientProcess == null) return;
        Container.ExecResult result = clientProcess.get(30, TimeUnit.SECONDS);
        Files.writeString(artifacts.resolve("binkd-client.log"), result.getStdout() + result.getStderr());
        assertEquals("binkd client must exit successfully", 0, result.getExitCode());
    }

    void assertTransferredFiles() throws Exception {
        byte[] received = container.copyFileFromContainer("/data/inbound/" + ExchangeFiles.FROM_JFTN,
                InputStream::readAllBytes);
        assertArrayEquals(files.toBinkd, received);
        assertEquals("binkd must remove the acknowledged outbound file", 0,
                container.execInContainer("sh", "-c", "test ! -e /data/outbox/" + ExchangeFiles.FROM_BINKD).getExitCode());
    }

    void saveDiagnostics(Path artifacts) {
        try {
            Files.writeString(artifacts.resolve("binkd-console.log"), String.join("", console));
            if (container.isRunning()) {
                container.copyFileFromContainer("/data/binkd.log", artifacts.resolve("binkd.log").toString());
            }
        } catch (Exception failure) {
            // Preserve the original startup/assertion failure, while still running cleanup.
            System.err.println("Could not save binkd diagnostics: " + failure);
        }
    }

    private void copyConfiguration(String endpoint) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("binkd/binkd.cfg")) {
            String config = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8)
                    .replace("@PEER_ENDPOINT@", endpoint);
            container.withCopyToContainer(Transferable.of(config.getBytes(StandardCharsets.UTF_8), 0644), "/data/binkd.cfg");
        }
    }

    private void saveEndpoint(Path artifacts, String endpoint) throws Exception {
        Files.writeString(artifacts.resolve("container.txt"), "id=" + container.getContainerId()
                + "\nendpoint=" + endpoint + "\n");
    }

    @Override public void close() {
        try {
            container.stop();
        } finally {
            if (caller != null) caller.shutdownNow();
        }
    }
}
