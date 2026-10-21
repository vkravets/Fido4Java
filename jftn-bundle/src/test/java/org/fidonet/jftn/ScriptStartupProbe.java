package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.tosser.Tosser;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fresh-JVM startup probe; all mutable state is below the explicitly supplied scratch path. */
public final class ScriptStartupProbe {
    private ScriptStartupProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(root);
        StringBuilder text = new StringBuilder("RobotAccessSource=files\nEchoSubscriptions=database\nNodeDirectoryEnabled=false\n");
        text.append("Tmp=").append(root.toString().replace('\\', '/')).append("\n");
        text.append("SysOp=Benchmark\nInbound=")
            .append(root.resolve("in").toString().replace('\\', '/'))
            .append("\nOutbound=")
            .append(root.resolve("out").toString().replace('\\', '/'))
            .append("\nEchoPath=")
            .append(root.resolve("echo").toString().replace('\\', '/'))
            .append("\nLink=1:500/101,1:500/100,secret,localhost\n");
        if (args.length > 1)
            text.append("MailScriptCache=").append(args[1]).append("\n");
        if (args.length > 2)
            text.append("MailScriptCompileThreads=").append(args[2]).append("\n");
        for (String script : List.of("areafix", "filefix", "fileecho-tools", "directory-import")) {
            text.append("MailScript=")
                .append(Path.of("src/main/resources/mail-scripts", script + ".groovy")
                            .toAbsolutePath()
                            .toString()
                            .replace('\\', '/'))
                .append("\n");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)));
        MailDatabaseFacade storage = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:script-probe"));
        if (!storage.open())
            throw new IllegalStateException("Cannot open isolated probe storage");
        long started = System.nanoTime();
        try (MailAutomationRuntime runtime = new MailAutomationRuntime(config,
                                                                       null,
                                                                       new Tosser(config, storage),
                                                                       List.of())) {
            long elapsed = System.nanoTime() - started;
            long heap = ManagementFactory.getMemoryPoolMXBeans()
                                         .stream()
                                         .filter(pool -> pool.getType() == MemoryType.HEAP)
                                         .mapToLong(pool -> pool.getPeakUsage().getUsed())
                                         .sum();
            System.out.printf("STARTUP elapsedMs=%.3f peakHeapMiB=%.1f processors=%d maxHeapMiB=%d%n",
                              elapsed / 1_000_000.0,
                              heap / 1048576.0,
                              Runtime.getRuntime().availableProcessors(),
                              Runtime.getRuntime().maxMemory() / 1048576);
        } finally {
            storage.close();
        }
    }
}
