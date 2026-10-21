package org.fidonet.jftn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Starts separate JVMs so measurements cannot reuse JIT or Groovy's in-process caches. */
public final class ScriptStartupMatrix {
    private ScriptStartupMatrix() {
    }

    public static void main(String[] args) throws Exception {
        Path reports = Path.of(args[0]).resolve(Long.toString(System.currentTimeMillis()));
        Files.createDirectories(reports);
        for (String threads : List.of("1", "2", "4", "5", "10")) {
            for (int trial = 1; trial <= 3; trial++) {
                run(reports, "uncached-" + threads + "-" + trial, "false", threads);
            }
        }
        run(reports, "fill", "true", "1");
        for (String threads : List.of("1", "2", "4", "auto")) {
            for (int trial = 1; trial <= 3; trial++) {
                run(reports, "cached-" + threads + "-" + trial, "true", threads);
            }
        }
        System.out.println("Script startup reports: " + reports);
    }

    private static void run(Path reports, String label, String cache, String threads)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx512m");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ScriptStartupProbe.class.getName());
        command.add(reports.resolve(cache.equals("true") ? "cached-state" : label).toString());
        command.add(cache);
        command.add(threads);
        Path log = reports.resolve(label + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                                                     .redirectOutput(log.toFile())
                                                     .start();
        if (process.waitFor() != 0)
            throw new IllegalStateException("Probe failed: " + log);
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            if (line.startsWith("STARTUP ") || line.contains("Mail script startup:")) {
                System.out.println(label + " " + line);
            }
        }
    }
}
