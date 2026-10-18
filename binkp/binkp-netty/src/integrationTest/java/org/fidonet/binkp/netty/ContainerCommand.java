package org.fidonet.binkp.netty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bounded native-command capture for the SSH fixture, independent of a Docker API socket. */
final class ContainerCommand {
    private ContainerCommand() {
    }

    static String run(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> drain(process.getInputStream(), output),
                                   "ssh-fixture-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("Fixture command timed out: " + command.get(0));
            }
            reader.join(5000);
            String text = output.toString(StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("Fixture command failed ("
                                      + process.exitValue()
                                      + "): "
                                      + text);
            }
            return text.trim();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void drain(InputStream input, ByteArrayOutputStream output) {
        try (InputStream stream = input) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                int allowed = Math.min(count, 1024 * 1024 - output.size());
                if (allowed > 0) {
                    output.write(buffer, 0, allowed);
                }
            }
        } catch (IOException ignored) {
            // Closing a timed-out native process can interrupt its output reader.
        }
    }
}
