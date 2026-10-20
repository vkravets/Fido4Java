package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class H2ShutdownTest {
    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completedFinalMailWorkAfterTheH2ExitHook(boolean legacyAutomaticClose) throws Exception {
        String result = runProbe(legacyAutomaticClose);
        if (legacyAutomaticClose) {
            assertTrue(result.contains("Connection has already been closed"), result);
            return;
        }
        assertEquals("OK", result);
        verifyFinalCommit();
    }

    private String runProbe(boolean legacyAutomaticClose) throws Exception {
        Path output = root.resolve("child-output.log");
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder command = new ProcessBuilder(executable,
                                                    "-cp",
                                                    childClasspath(),
                                                    H2ShutdownProbe.class.getName(),
                                                    legacyAutomaticClose ? "legacy" : "managed");
        command.directory(root.toFile());
        command.redirectErrorStream(true);
        command.redirectOutput(output.toFile());
        Process process = command.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Shutdown probe did not exit");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            process.destroyForcibly();
        }
        return Files.readString(root.resolve("shutdown-result.txt"));
    }

    private void verifyFinalCommit() throws Exception {
        MailDatabaseFacade reopened = new MailDatabaseFacade(new OrmManager("jdbc:h2:"
                + root.resolve("database/jftn10") + ";IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE"));
        try {
            assertTrue(reopened.open());
            assertTrue(reopened.getAreas().contains("shutdown-completed"));
        } finally {
            reopened.close();
        }
    }

    private String childClasspath() throws Exception {
        Set<String> entries = new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path")
                                                                      .split(File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader) {
                addClasspath(entries, (URLClassLoader) loader);
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private void addClasspath(Set<String> entries, URLClassLoader loader) throws Exception {
        for (URL url : loader.getURLs()) {
            if (url.getProtocol().equals("file")) {
                entries.add(Path.of(url.toURI()).toString());
            }
        }
    }
}
