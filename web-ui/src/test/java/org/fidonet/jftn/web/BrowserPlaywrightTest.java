package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Runs real Chromium against compiled Vue assets and real HTTP RPC handlers. */
@Tag("browser")
class BrowserPlaywrightTest {
    @TempDir
    Path directory;

    @Test
    void completedAccountWorkflowThroughTheBrowser() throws Exception {
        runBrowser("application", "");
    }

    @Test
    void loadedAndValidatedTheInitialSetupThroughTheBrowser() throws Exception {
        runBrowser("setup", "isolated-browser-setup-token");
    }

    private void runBrowser(String scenario, String setupToken) throws Exception {
        try (BrowserServerFixture server = new BrowserServerFixture(directory, setupToken)) {
            Path output = output(scenario);
            ProcessBuilder command = new ProcessBuilder(System.getProperty("ui.node"),
                                                        System.getProperty("ui.playwright.cli"),
                                                        "test",
                                                        "--config",
                                                        "playwright.app.config.ts");
            command.directory(Path.of(System.getProperty("ui.frontend")).toFile());
            command.environment().put("UI_BASE_URL", server.origin());
            command.environment().put("UI_SCENARIO", scenario);
            command.environment().put("UI_SETUP_TOKEN", setupToken);
            Process process = command.redirectErrorStream(true)
                                     .redirectOutput(output.toFile())
                                     .start();
            try {
                assertTrue(process.waitFor(180, TimeUnit.SECONDS),
                           "Playwright timed out: " + output);
                assertEquals(0, process.exitValue(), Files.readString(output));
            } finally {
                stop(process);
            }
        }
    }

    private Path output(String scenario) throws IOException {
        Path reports = Path.of(System.getProperty("ui.browserReports"));
        Files.createDirectories(reports);
        return reports.resolve(scenario + "-process.log");
    }

    private void stop(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
