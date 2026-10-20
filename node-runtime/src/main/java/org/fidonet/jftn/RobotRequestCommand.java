package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.MailRepository;
import org.fidonet.jftn.script.RemoteRobotRequests;
import org.fidonet.jftn.script.RobotRequest;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.storage.StorageSession;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Offline request submission; secrets come from a private file, never command-line arguments. */
final class RobotRequestCommand {
    private RobotRequestCommand() {
    }

    static boolean handle(String[] args) throws Exception {
        if (args.length == 0 || !"--robot-request".equals(args[0])) {
            return false;
        }
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Expected --robot-request request-file [configuration-file]");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(args.length == 3 ? args[2] : "jftn.conf");
        Path temporary = Path.of(config.getTmpDir());
        Files.createDirectories(temporary);
        try (FileChannel channel = FileChannel.open(temporary.resolve("mail-service.lock"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE);
                FileLock lock = lock(channel)) {
            Properties request = new Properties();
            Path source = Path.of(args[1]);
            if (Files.size(source) > 256000) {
                throw new IllegalArgumentException("Robot request file exceeds 256 KiB");
            }
            try (Reader reader = Files.newBufferedReader(source)) {
                request.load(reader);
            }
            queue(config, request);
        }
        return true;
    }

    static String queue(JFtnConfig config, Properties request) throws Exception {
        try (StorageSession storage = OfflineMailStorage.open(config, true)) {
            MailRepository database = OfflineMailStorage.repository(storage);
            String key = submit(config, new Tosser(config, database), request);
            System.out.println("Queued robot request: " + key);
            return key;
        }
    }

    static String submit(JFtnConfig config, Tosser tosser, Properties request) throws IOException {
        return new RemoteRobotRequests(config,
                                       tosser,
                                       new ScriptState(Path.of(config.getTmpDir(), "scripts")))
                                                                                               .submit(required(request,
                                                                                                                "Robot"),
                                                                                                       new FTNAddr(required(request,
                                                                                                                            "To")),
                                                                                                       new FTNAddr(required(request,
                                                                                                                            "From")),
                                                                                                       required(request,
                                                                                                                "Password"),
                                                                                                       RobotRequest.commands(required(request,
                                                                                                                                      "Commands")),
                                                                                                       required(request,
                                                                                                                "RequestId"));
    }

    private static String required(Properties request, String key) {
        String value = request.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing robot request field: " + key);
        }
        return value;
    }

    private static FileLock lock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return lock;
            }
        } catch (OverlappingFileLockException busy) {
            throw new IOException("Stop the station before offline robot requests", busy);
        }
        throw new IOException("Stop the station before offline robot requests");
    }
}
