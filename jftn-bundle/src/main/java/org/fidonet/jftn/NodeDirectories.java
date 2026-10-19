package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.RuntimeProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Creates storage before plugins start and fails early when a path is not writable/a directory. */
final class NodeDirectories {
    private NodeDirectories() {
    }

    static void defaults(Path root) throws IOException {
        defaults(root, RuntimeProfile.FULL);
    }

    static void defaults(Path root, RuntimeProfile profile) throws IOException {
        for (String directory : List.of("mail/inbound",
                                        "mail/outbound",
                                        "mail/tmp",
                                        "mail/echo",
                                        "mail/received",
                                        "conf.d",
                                        "database",
                                        "logs",
                                        "lists",
                                        "fileecho")) {
            if (directory.equals("database") && !profile.usesSql()) {
                continue;
            }
            Files.createDirectories(root.resolve(directory));
        }
        PluginConfigurationFiles.createMissing(root.resolve("conf.d"), profile);
    }

    static void configured(JFtnConfig config) throws IOException {
        if (config.getSourcePath() != null) {
            PluginConfigurationFiles.createMissing(config.getSourcePath()
                                                         .getParent()
                                                         .resolve("conf.d"),
                                                   RuntimeProfile.read(config));
        }
        for (String key : List.of("Inbound", "Outbound", "Tmp", "EchoPath")) {
            Files.createDirectories(Path.of(config.getValue(key)));
        }
        if (config.isPreserveReceivedFiles()) {
            Files.createDirectories(config.getReceivedArchivePath());
        }
    }
}
