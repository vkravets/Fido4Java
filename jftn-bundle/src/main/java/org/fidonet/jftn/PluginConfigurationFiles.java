package org.fidonet.jftn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Installs editable plugin templates once; upgrades never replace operator configuration. */
final class PluginConfigurationFiles {
    static final List<String> NAMES = List.of("10-binkp.conf",
                                              "20-jam-bridge.conf",
                                              "26-bso.conf",
                                              "27-virtual-outbound.conf",
                                              "30-nodelist.conf",
                                              "32-fileecho.conf",
                                              "40-mail-scripts.conf",
                                              "41-areafix.conf",
                                              "42-filefix.conf",
                                              "43-directory-feed.conf",
                                              "44-fileecho-tools.conf",
                                              "90-rpc.conf");

    private PluginConfigurationFiles() {
    }

    static void createMissing(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (String name : NAMES) {
            copyMissing(directory.resolve(name), name);
        }
    }

    private static void copyMissing(Path destination, String name) throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        try (InputStream input = PluginConfigurationFiles.class.getResourceAsStream("/plugin-config/"
                + name)) {
            if (input == null) {
                throw new IOException("Missing bundled plugin configuration: " + name);
            }
            Files.copy(input, destination);
        } catch (FileAlreadyExistsException alreadyCreated) {
            // A concurrent start must not replace an existing operator file.
        }
    }
}
