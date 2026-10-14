import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Properties;

/** Source-file launcher: keeps the Gradle Wrapper binary out of version control. */
class DownloadWrapper {
    public static void main(String[] args) {
        try {
            download(Path.of(args[0]).toAbsolutePath().normalize());
        } catch (Exception e) {
            System.err.println("ERROR: Cannot prepare Gradle Wrapper: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void download(Path root) throws Exception {
        Path propertiesFile = root.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }
        String expected = required(properties, "wrapperSha256Sum");
        if (!expected.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid wrapperSha256Sum");
        }
        URL url = URI.create(required(properties, "wrapperUrl")).toURL();
        if (!url.getProtocol().equals("https")) {
            throw new IllegalArgumentException("wrapperUrl must use HTTPS");
        }
        int timeout = Integer.parseInt(properties.getProperty("networkTimeout", "10000"));
        if (timeout <= 0) {
            throw new IllegalArgumentException("networkTimeout must be positive");
        }
        Path cache = root.resolve(".gradle/wrapper-bootstrap");
        Files.createDirectories(cache);
        Path jar = cache.resolve("gradle-wrapper.jar");
        try (FileChannel lockChannel = FileChannel.open(cache.resolve("download.lock"),
                                                        StandardOpenOption.CREATE,
                                                        StandardOpenOption.WRITE);
                FileLock lock = lockChannel.lock()) {
            if (!Files.isRegularFile(jar) || !expected.equalsIgnoreCase(checksum(jar))) {
                System.err.println("Downloading Gradle Wrapper from " + url);
                Path temporary = Files.createTempFile(cache, "wrapper-", ".part");
                try {
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);
                    try {
                        if (connection.getResponseCode() != 200) {
                            throw new IOException("HTTP " + connection.getResponseCode());
                        }
                        try (InputStream input = connection.getInputStream()) {
                            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally {
                        connection.disconnect();
                    }
                    String actual = checksum(temporary);
                    if (!expected.equalsIgnoreCase(actual)) {
                        throw new IOException("Wrapper SHA-256 mismatch: expected "
                                              + expected
                                              + ", got "
                                              + actual);
                    }
                    replace(temporary, jar);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            // WrapperMain expects this file next to its JAR; two cache directories preserve its
            // project-root calculation.
            Path temporary = Files.createTempFile(cache, "properties-", ".part");
            try {
                Files.copy(propertiesFile, temporary, StandardCopyOption.REPLACE_EXISTING);
                replace(temporary, cache.resolve("gradle-wrapper.properties"));
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value.trim();
    }

    private static String checksum(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source,
                       target,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
