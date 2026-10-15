package org.fidonet.jftn.tosser.interop;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Owns container lifecycle, process execution and diagnostics, without host bind mounts. */
final class HptContainer implements AutoCloseable {
    private static final ImageFromDockerfile IMAGE = new ImageFromDockerfile().withFileFromClasspath("Dockerfile",
                                                                                                     "hpt/Dockerfile")
                                                                              .withFileFromClasspath("revisions.txt",
                                                                                                     "hpt/revisions.txt");
    private final GenericContainer<?> container;
    private final String name;
    private final String root;
    private final Path artifacts;
    private final List<String> commands = new ArrayList<>();
    private final ExecutorService workers = Executors.newSingleThreadExecutor();
    private Future<?> lockHolder;

    HptContainer(String name) throws IOException {
        this.name = name;
        root = "/data/" + name;
        artifacts = Path.of(System.getProperty("hpt.artifactsDir"), name);
        Files.createDirectories(artifacts);
        String image = System.getProperty("hpt.image");
        container = image == null ? new GenericContainer<>(IMAGE) : new GenericContainer<>(image);
        container.waitingFor(Wait.forLogMessage(".*HPT_READY.*\\n", 1))
                 .withStartupTimeout(Duration.ofMinutes(3));
    }

    void start() throws Exception {
        container.start();
        container.copyFileToContainer(MountableFile.forHostPath(System.getProperty("hpt.workerFiles")),
                                      "/opt/jftn/lib");
        worker("init");
        copyText(root + "/hpt.cfg", resource("hpt/hpt.cfg").replace("@ROOT@", root));
        copyText(root + "/message.txt", "\1CHRS: UTF-8 4\n" + HptMessages.TEXT + "\n");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void copyText(String path, String text) {
        container.copyFileToContainer(Transferable.of(text.getBytes(StandardCharsets.UTF_8), 0644),
                                      path);
    }

    void worker(String command) throws Exception {
        execute("timeout",
                "60s",
                "java",
                "-Xmx256m",
                "-cp",
                "/opt/jftn/lib/*",
                HptInteropWorker.class.getName(),
                command,
                root);
    }

    void hpt(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList("timeout",
                                                             "60s",
                                                             "hpt",
                                                             "-c",
                                                             root + "/hpt.cfg"));
        command.addAll(Arrays.asList(arguments));
        execute(command.toArray(new String[0]));
    }

    void postMessages() throws Exception {
        postEcho();
        postNetmail();
    }

    void postEcho() throws Exception {
        hpt("post",
            "-e",
            "TEST",
            "-nf",
            "HPT Author",
            "-nt",
            "All",
            "-s",
            "HPT Тема",
            "-af",
            "2:500/102",
            "-f",
            "loc",
            root + "/message.txt");
    }

    private void postNetmail() throws Exception {
        hpt("post",
            "-nf",
            "HPT Author",
            "-nt",
            "Remote",
            "-s",
            "HPT Тема",
            "-af",
            "2:500/102",
            "-at",
            "2:500/101.3",
            "-f",
            "loc pvt",
            root + "/message.txt");
    }

    void holdJavaLock() throws Exception {
        lockHolder = workers.submit(() -> {
            worker("hold-lock");
            return null;
        });
        execute("timeout",
                "10s",
                "sh",
                "-c",
                "until test -f \"$1/lock-held\"; do sleep 0.1; done",
                "sh",
                root);
    }

    int postWhileLocked() throws Exception {
        Container.ExecResult result = container.execInContainer("timeout",
                                                                "2s",
                                                                "hpt",
                                                                "-c",
                                                                root + "/hpt.cfg",
                                                                "post",
                                                                "-e",
                                                                "TEST",
                                                                "-nf",
                                                                "HPT Author",
                                                                "-nt",
                                                                "All",
                                                                "-s",
                                                                "HPT Тема",
                                                                "-af",
                                                                "2:500/102",
                                                                "-f",
                                                                "loc",
                                                                root + "/message.txt");
        recordCommand("HPT post while Java owns the JAM lock", result);
        return result.getExitCode();
    }

    void releaseJavaLock() throws Exception {
        execute("touch", root + "/release-lock");
        lockHolder.get(10, TimeUnit.SECONDS);
    }

    private void execute(String... command) throws Exception {
        Container.ExecResult result = container.execInContainer(command);
        recordCommand(String.join(" ", command), result);
        if (result.getExitCode() != 0) {
            throw new AssertionError("External process failed: "
                                     + String.join(" ", command)
                                     + "\n"
                                     + result.getStdout()
                                     + result.getStderr());
        }
    }

    private synchronized void recordCommand(String command, Container.ExecResult result)
            throws IOException {
        commands.add(command
                     + "\nexit="
                     + result.getExitCode()
                     + "\n"
                     + result.getStdout()
                     + "\n"
                     + result.getStderr());
        Files.writeString(artifacts.resolve("commands.log"), String.join("\n", commands));
    }

    private void saveDiagnostics() {
        try {
            Files.writeString(artifacts.resolve("container.log"), container.getLogs());
            if (!container.isRunning()) {
                return;
            }
            container.execInContainer("tar",
                                      "-czf",
                                      "/data/" + name + ".tar.gz",
                                      "-C",
                                      "/data",
                                      name);
            container.copyFileFromContainer("/data/" + name + ".tar.gz",
                                            artifacts.resolve("scenario.tar.gz").toString());
            container.copyFileFromContainer("/opt/hpt-revisions.txt",
                                            artifacts.resolve("revisions.txt").toString());
        } catch (Exception failure) {
            System.err.println("Unable to collect HPT diagnostics: " + failure);
        }
    }

    @Override
    public void close() {
        try {
            saveDiagnostics();
        } finally {
            workers.shutdownNow();
            container.stop();
        }
    }
}
