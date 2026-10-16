package org.fidonet.gradle.distribution;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Downloads a locked vendor archive; never publishes an unverified runtime to the cache. */
public abstract class DownloadRuntime extends DefaultTask {
    @Input
    public abstract Property<String> getUrl();

    @Input
    public abstract Property<String> getSha256();

    @OutputFile
    public abstract RegularFileProperty getArchive();

    @TaskAction
    public void download() throws Exception {
        Path destination = getArchive().get().getAsFile().toPath();
        Files.createDirectories(destination.getParent());
        if (Files.isRegularFile(destination) && matches(destination, getSha256().get())) {
            return;
        }
        Path temporary = Files.createTempFile(destination.getParent(), "runtime-", ".part");
        try {
            fetch(temporary);
            Files.move(temporary,
                       destination,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean matches(Path archive, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(archive), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
    }

    private void fetch(Path temporary) throws Exception {
        URI uri = URI.create(getUrl().get());
        if (!"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Runtime URL must use HTTPS");
        }
        HttpClient client = HttpClient.newBuilder()
                                      .connectTimeout(Duration.ofSeconds(30))
                                      .followRedirects(HttpClient.Redirect.NORMAL)
                                      .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .timeout(Duration.ofMinutes(5))
                                         .GET()
                                         .build();
        HttpResponse<InputStream> response = client.send(request,
                                                         HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body();
                OutputStream output = Files.newOutputStream(temporary)) {
            if (response.statusCode() != 200) {
                throw new IOException("Runtime download returned HTTP " + response.statusCode());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = copy(input, output, digest);
            String actual = HexFormat.of().formatHex(digest.digest());
            if (total == 0 || !actual.equalsIgnoreCase(getSha256().get())) {
                throw new IOException("Runtime SHA-256 mismatch for " + uri);
            }
        }
    }

    private static long copy(InputStream input, OutputStream output, MessageDigest digest)
            throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > 512L * 1024 * 1024 || System.nanoTime() > deadline) {
                throw new IOException("Runtime download exceeded its size/time limit");
            }
            digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
        return total;
    }
}
