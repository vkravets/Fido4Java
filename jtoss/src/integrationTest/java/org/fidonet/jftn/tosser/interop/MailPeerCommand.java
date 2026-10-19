package org.fidonet.jftn.tosser.interop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Argument-safe CLI boundary: no shell, engine socket mounts or privileged containers. */
final class MailPeerCommand {
    private final String engine;
    private final Path artifacts;
    private int sequence;

    MailPeerCommand(Path artifacts) throws IOException, InterruptedException {
        this.artifacts = artifacts;
        Files.createDirectories(artifacts);
        engine = System.getProperty("mailInterop.engine", "docker");
        if (!List.of("docker", "podman").contains(engine)) {
            throw new IllegalArgumentException("mailInterop.engine must be docker or podman");
        }
        execute("info");
    }

    synchronized String execute(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(engine);
        command.addAll(Arrays.asList(arguments));
        Path output = artifacts.resolve(String.format("command-%03d.log", ++sequence));
        Files.writeString(output.resolveSibling(output.getFileName() + ".args"),
                          String.join("\n", command));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                                                     .redirectOutput(output.toFile())
                                                     .start();
        try {
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                throw new IOException("Container command timed out; see " + output);
            }
            String text = Files.readString(output);
            if (process.exitValue() != 0) {
                throw new IOException("Container command failed ("
                                      + process.exitValue()
                                      + "); see "
                                      + output
                                      + "\n"
                                      + text);
            }
            return text.trim();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
