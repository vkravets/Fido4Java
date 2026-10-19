package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Isolated installed-artifact operations; child JVMs never use application test classpaths. */
final class InstalledNodeFixture {
    static final String BINKP = "binkp server listening: port=([0-9]+)";
    static final String TCP = "RPC service listening: transport=tcp, port=([0-9]+)";
    static final String HTTP = "RPC service listening: transport=http, port=([0-9]+)";
    static final String SETUP = "Configure JFtn: http://localhost:([0-9]+)/";

    private InstalledNodeFixture() {
    }

    static Path unpack(Path temporary, String profile) throws IOException {
        Path destination = temporary.resolve(profile).toAbsolutePath().normalize();
        Files.createDirectories(destination);
        Path archive = Path.of(System.getProperty("profile.distribution." + profile));
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                copy(input, entry, destination);
            }
        }
        try (var paths = Files.list(destination)) {
            return paths.filter(Files::isDirectory).findFirst().orElseThrow();
        }
    }

    private static void copy(ZipInputStream input, ZipEntry entry, Path destination)
            throws IOException {
        Path target = destination.resolve(entry.getName()).normalize();
        assertTrue(target.startsWith(destination));
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static void configure(Path home, String profile) throws IOException {
        try (var fragments = Files.list(home.resolve("conf.d"))) {
            for (Path path : fragments.filter(path -> path.toString().endsWith(".conf.example"))
                                      .collect(Collectors.toList())) {
                Files.copy(path,
                           path.resolveSibling(path.getFileName()
                                                   .toString()
                                                   .replace(".example", "")));
            }
        }
        Files.writeString(home.resolve("jftn.conf"),
                          "SysOp=Test\nRuntimeProfile=" + profile
                                  + "\nLink=1:500/101,1:500/100,-\nLink=1:500/102,1:500/100,-\n"
                                  + "Inbound=./mail/inbound\nOutbound=./mail/outbound\nTmp=./mail/tmp\nEchoPath=./mail/echo\n"
                                  + "RpcEnabled=false\nRobotAccessSource=files\nFileEchoSource=files\n"
                                  + "NodeDirectoryEnabled=false\nJamBridgeEnabled=false\n"
                                  + "EchoArea=test,1:500/101=WRITE,1:500/102=READ\n"
                                  + "DefaultRoute=1:500/102\nAllowTransit=true\nTransitFrom=1:500/101\nTransitTo=*\nBinkpPort=0\n"
                                  + "FileArea=lists.test|./fileecho/lists.test|Test files\nTicPeer=1:500/101|tic-password|lists.*\n");
    }

    static NodeProcess start(Path home, String... arguments) throws IOException {
        Files.writeString(home.resolve("conf.d/26-bso.conf"), "BsoEnabled=true\n");
        Path log = home.resolve("installed-node.log");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Djftn.setup.port=0");
        command.add("-cp");
        command.add(String.join(File.pathSeparator,
                                "scripts",
                                "lib/*",
                                "lib/groovy/*",
                                "plugins/*"));
        command.add(JFtnRunner.class.getName());
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).directory(home.toFile())
                                                     .redirectErrorStream(true)
                                                     .redirectOutput(log.toFile())
                                                     .start();
        return new NodeProcess(process, log);
    }

    static final class NodeProcess implements AutoCloseable {
        private final Process process;
        private final Path log;

        private NodeProcess(Process process, Path log) {
            this.process = process;
            this.log = log;
        }

        String output() throws IOException {
            return Files.exists(log)
                    ? Files.readString(log).replaceAll("#setup=\\S+", "#setup=[redacted]")
                    : "";
        }

        Matcher await(String expression) throws Exception {
            Pattern pattern = Pattern.compile(expression);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            while (System.nanoTime() < deadline) {
                Matcher match = pattern.matcher(output());
                if (match.find()) {
                    return match;
                }
                assertTrue(process.isAlive(), output());
                Thread.sleep(100);
            }
            throw new AssertionError("Timed out waiting for " + expression + ": " + output());
        }

        int port(String expression) throws Exception {
            return Integer.parseInt(await(expression).group(1));
        }

        int awaitExit() throws Exception {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS),
                       "Installed node did not exit: " + output());
            return process.exitValue();
        }

        @Override
        public void close() throws Exception {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Installed node did not stop");
            }
        }
    }
}
