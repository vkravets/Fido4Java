package org.fidonet.nodelist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Bounded HTTP(S) downloads reuse the validated, recoverable directory publication workflow. */
public final class HttpDirectoryDownload {
    public static final String DEFAULT_URL = "https://nodelist.fidonet.cc/download/latest?domain=fidonet";
    private static final long MAX_BYTES = 128L * 1024 * 1024;

    private HttpDirectoryDownload() {
    }

    public static DirectoryImportResult install(DirectoryCatalog catalog,
                                                DirectorySource source,
                                                URI url)
            throws IOException {
        validate(url);
        Files.createDirectories(source.getPath().getParent());
        Path incoming = Files.createTempFile(source.getPath().getParent(),
                                             ".http-directory-",
                                             ".tmp");
        try {
            download(url, incoming);
            return DirectoryUpdate.install(catalog, source, incoming, false);
        } finally {
            Files.deleteIfExists(incoming);
        }
    }

    private static void validate(URI url) {
        if (url == null || !("https".equals(url.getScheme()) || "http".equals(url.getScheme()))
                || url.getHost() == null || url.getUserInfo() != null
                || url.getFragment() != null) {
            throw new IllegalArgumentException("Directory URL must be HTTP(S), without credentials or fragments");
        }
    }

    private static void download(URI initial, Path target) throws IOException {
        URI current = initial;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        for (int redirects = 0; redirects <= 5; redirects++) {
            URI next = downloadOnce(current, target, deadline);
            if (next == null) {
                return;
            }
            current = next;
        }
        throw new IOException("Too many directory download redirects");
    }

    private static URI downloadOnce(URI url, Path target, long deadline) throws IOException {
        requireTime(deadline);
        validate(url);
        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "JFtn directory importer");
            return readResponse(connection, url, target, deadline);
        } finally {
            connection.disconnect();
        }
    }

    private static URI readResponse(HttpURLConnection connection,
                                    URI url,
                                    Path target,
                                    long deadline)
            throws IOException {
        int status = connection.getResponseCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            return redirect(url, connection.getHeaderField("Location"));
        }
        if (status != 200 || connection.getContentLengthLong() > MAX_BYTES) {
            throw new IOException("Directory download failed or exceeds 128 MiB (HTTP "
                                  + status
                                  + ")");
        }
        try (InputStream input = connection.getInputStream();
                OutputStream output = Files.newOutputStream(target)) {
            copy(input, output, deadline);
        }
        return null;
    }

    private static URI redirect(URI previous, String location) throws IOException {
        if (location == null) {
            throw new IOException("Directory redirect has no Location header");
        }
        URI next = previous.resolve(location);
        validate(next);
        if (previous.getScheme().equals("https") && !next.getScheme().equals("https")) {
            throw new IOException("Directory redirect cannot downgrade HTTPS");
        }
        return next;
    }

    private static void copy(InputStream input, OutputStream output, long deadline)
            throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            requireTime(deadline);
            total += count;
            if (total > MAX_BYTES) {
                throw new IOException("Directory download exceeds 128 MiB");
            }
            output.write(buffer, 0, count);
        }
    }

    private static void requireTime(long deadline) throws IOException {
        if (System.nanoTime() > deadline) {
            throw new IOException("Directory download exceeded its time limit");
        }
    }
}
