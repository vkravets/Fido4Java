package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BrowserUiTest {
    @TempDir
    Path directory;

    @Test
    void servedCompiledVueAssetsAndProtectedFilesystemPaths() throws Exception {
        try (BrowserServerFixture server = new BrowserServerFixture(directory)) {
            HttpResponse<String> index = get(server, "/");
            assertEquals(200, index.statusCode());
            assertTrue(index.body().contains("<div id=\"app\"></div>"));
            assertTrue(index.headers()
                            .firstValue("Content-Security-Policy")
                            .orElseThrow()
                            .contains("script-src 'self'"));
            assertEquals("no-store", index.headers().firstValue("Cache-Control").orElseThrow());
            Matcher script = Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"").matcher(index.body());
            assertTrue(script.find());
            HttpResponse<String> asset = get(server, script.group(1));
            assertEquals(200, asset.statusCode());
            assertTrue(asset.headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
                            .startsWith("text/javascript"));
            assertTrue(asset.headers()
                            .firstValue("Cache-Control")
                            .orElseThrow()
                            .contains("immutable"));
            assertEquals(200, get(server, "/settings").statusCode());
            assertEquals(404, get(server, "/missing.js").statusCode());
            assertEquals(404, get(server, "/%2e%2e/jftn.conf").statusCode());
            assertFalse(index.body().contains("preview-admin-password"));
        }
    }

    @Test
    void completedSignupApprovalAndProfileChangesUsingTheGeneratedBrowserClient() throws Exception {
        try (BrowserServerFixture server = new BrowserServerFixture(directory)) {
            Path output = directory.resolve("browser-workflow.log");
            Process process = new ProcessBuilder(System.getProperty("ui.node"),
                                                 "--experimental-strip-types",
                                                 System.getProperty("ui.workflow"),
                                                 server.origin()).redirectErrorStream(true)
                                                                 .redirectOutput(output.toFile())
                                                                 .start();
            try {
                assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Browser workflow timed out");
                assertEquals(0, process.exitValue(), Files.readString(output));
            } finally {
                process.destroyForcibly();
            }
        }
    }

    private HttpResponse<String> get(BrowserServerFixture server, String path) throws Exception {
        return HttpClient.newHttpClient()
                         .send(HttpRequest.newBuilder(URI.create(server.origin() + path))
                                          .GET()
                                          .build(),
                               HttpResponse.BodyHandlers.ofString());
    }
}
