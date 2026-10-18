package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.plugins.OfflineCommand;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/** Explicit offline rebuild using archived TICs and the operator's external formatting script. */
public final class FileEchoIndexCommand implements OfflineCommand {
    @Override
    public boolean handle(String[] arguments) throws Exception {
        if (arguments.length == 0 || !arguments[0].equals("--rebuild-fileecho-index")) {
            return false;
        }
        if (arguments.length < 3 || arguments.length > 4) {
            throw new IllegalArgumentException("Expected --rebuild-fileecho-index AREA directory [configuration-file]");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(arguments.length == 4 ? arguments[3] : "jftn.conf");
        Path temporary = Path.of(config.getTmpDir());
        Files.createDirectories(temporary);
        try (FileChannel channel = FileChannel.open(temporary.resolve("mail-service.lock"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE);
                FileLock lock = lock(channel)) {
            rebuild(config, arguments[1], Path.of(arguments[2]));
        }
        return true;
    }

    private static FileLock lock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return lock;
            }
        } catch (OverlappingFileLockException busy) {
            throw new IOException("Stop the station before rebuilding its fileecho index", busy);
        }
        throw new IOException("Stop the station before rebuilding its fileecho index");
    }

    private static void rebuild(JFtnConfig config, String area, Path directory) throws Exception {
        Path script = script(config);
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
        if (!(engine instanceof Invocable)) {
            throw new IOException("Groovy script engine is unavailable");
        }
        List<Map<String, Object>> rows = FileEchoIndexRecovery.recover(area,
                                                                       directory,
                                                                       config.getReceivedArchivePath(),
                                                                       Charset.forName(config.getValue("TicCharset",
                                                                                                       "IBM437")));
        try (Reader reader = Files.newBufferedReader(script)) {
            engine.eval(reader);
        }
        Object output = ((Invocable) engine).invokeFunction("rebuildIndex",
                                                            Map.of("config",
                                                                   config,
                                                                   "scriptDirectory",
                                                                   script.getParent()),
                                                            directory,
                                                            rows);
        long described = rows.stream()
                             .filter(row -> !((List<?>) row.get("descriptions")).isEmpty())
                             .count();
        System.out.println("Rebuilt "
                           + output
                           + ": "
                           + rows.size()
                           + " files, "
                           + described
                           + " with verified TIC descriptions; "
                           + (rows.size() - described)
                           + " without descriptions");
    }

    private static Path script(JFtnConfig config) throws IOException {
        List<String> scripts = config.getValuesAsList("MailScript");
        if (scripts != null) {
            for (String entry : scripts) {
                if (!entry.startsWith("classpath:") && Path.of(entry)
                                                           .getFileName()
                                                           .toString()
                                                           .equals("fileecho-tools.groovy")) {
                    return Path.of(entry).toAbsolutePath().normalize().toRealPath();
                }
            }
        }
        throw new IOException("Configure an external fileecho-tools.groovy MailScript first");
    }
}
