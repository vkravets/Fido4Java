package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class DistributionLayoutTest {
    @TempDir
    Path temporary;

    @Test
    void packagedSeparateLibrariesPluginsAndScriptsAndAllLaunchers() throws Exception {
        Path home = unpack();
        for (String launcher : List.of("jftn.sh", "jftn.cmd", "jftn.ps1")) {
            assertTrue(Files.isRegularFile(home.resolve(launcher)));
        }
        assertFalse(Files.exists(home.resolve("jftn.conf")));
        for (String name : PluginConfigurationFiles.NAMES) {
            assertTrue(Files.isRegularFile(home.resolve("conf.d").resolve(name + ".example")));
            assertFalse(Files.exists(home.resolve("conf.d").resolve(name)));
        }
        assertTrue(Files.isRegularFile(home.resolve("scripts/mail-scripts/areafix.groovy")));
        try (var plugins = Files.list(home.resolve("plugins"));
                var groovy = Files.list(home.resolve("lib/groovy"))) {
            List<String> names = plugins.map(path -> path.getFileName().toString())
                                        .collect(Collectors.toList());
            assertEquals(8, names.size());
            assertTrue(names.stream().anyMatch(name -> name.startsWith("rpc-node-")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("web-ui-")));
            assertTrue(groovy.count() > 1);
        }
        try (var files = Files.list(home.resolve("lib"))) {
            Path application = files.filter(path -> path.getFileName()
                                                        .toString()
                                                        .startsWith("jftn-bundle-"))
                                    .findFirst()
                                    .orElseThrow();
            try (ZipFile jar = new ZipFile(application.toFile())) {
                assertTrue(jar.getEntry("org/fidonet/jftn/JFtnRunner.class") != null);
                assertTrue(jar.getEntry("mail-scripts/areafix.groovy") == null);
                assertTrue(jar.getEntry("groovy/lang/GroovyShell.class") == null);
                assertTrue(jar.getEntry("org/fidonet/jftn/ui/NodeRpc.class") == null);
                assertTrue(jar.getEntry("org/fidonet/jftn/FullProfileServices.class") == null);
            }
        }
    }

    @Test
    void loadedPackagedPluginsAndGroovyInAFreshJvm() throws Exception {
        Path home = unpack();
        String separator = File.pathSeparator;
        String classpath = System.getProperty("distribution.probe") + separator
                + home.resolve("scripts") + separator + home.resolve("lib") + "/*" + separator
                + home.resolve("lib/groovy") + "/*" + separator + home.resolve("plugins") + "/*";
        Path output = temporary.resolve("probe.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java,
                                             "-cp",
                                             classpath,
                                             DistributionProbe.class.getName()).directory(home.toFile())
                                                                               .redirectErrorStream(true)
                                                                               .redirectOutput(output.toFile())
                                                                               .start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Distribution probe timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            process.destroyForcibly();
        }
    }

    private Path unpack() throws IOException {
        try (ZipFile archive = new ZipFile(System.getProperty("distribution.zip"))) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                copy(archive, entries.nextElement());
            }
        }
        try (var files = Files.list(temporary)) {
            return files.filter(Files::isDirectory).findFirst().orElseThrow();
        }
    }

    private void copy(ZipFile archive, ZipEntry entry) throws IOException {
        Path target = temporary.resolve(entry.getName()).normalize();
        if (!target.startsWith(temporary)) {
            throw new IOException("ZIP entry escaped extraction directory");
        }
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = archive.getInputStream(entry)) {
            Files.copy(input, target);
        }
    }
}
