package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Creates storage before plugins start and fails early when a path is not writable/a directory. */
final class NodeDirectories {
    private NodeDirectories() {
    }

    static void defaults(Path root) throws IOException {
        for (String directory : List.of("mail/inbound",
                                        "mail/outbound",
                                        "mail/tmp",
                                        "mail/echo",
                                        "database",
                                        "logs",
                                        "lists",
                                        "fileecho")) {
            Files.createDirectories(root.resolve(directory));
        }
    }

    static void configured(JFtnConfig config) throws IOException {
        for (String key : List.of("Inbound", "Outbound", "Tmp", "EchoPath")) {
            Files.createDirectories(Path.of(config.getValue(key)));
        }
    }
}
