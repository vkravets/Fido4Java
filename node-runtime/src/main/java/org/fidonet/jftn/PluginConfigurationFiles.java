package org.fidonet.jftn;

import org.fidonet.config.RuntimeProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
                                              "90-rpc.conf",
                                              "95-web-ui.conf");

    private PluginConfigurationFiles() {
    }

    static void createMissing(Path directory) throws IOException {
        createMissing(directory, RuntimeProfile.FULL);
    }

    static void createMissing(Path directory, RuntimeProfile profile) throws IOException {
        Files.createDirectories(directory);
        for (String name : NAMES) {
            copyMissing(directory.resolve(name), name, profile);
        }
    }

    private static void copyMissing(Path destination, String name, RuntimeProfile profile)
            throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        try (InputStream input = PluginConfigurationFiles.class.getResourceAsStream("/plugin-config/"
                + name)) {
            if (input == null) {
                throw new IOException("Missing bundled plugin configuration: " + name);
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (!profile.includesUiByDefault()) {
                text = text.replace("RpcEnabled=true", "RpcEnabled=false");
                text = text.replace("EchoSubscriptions=database", "EchoSubscriptions=config");
                text = text.replace("RobotAccessSource=database", "RobotAccessSource=files");
                text = text.replace("FileEchoSource=database", "FileEchoSource=files");
            }
            if (!profile.usesSql()) {
                text = text.replace("EchoSubscriptions=database", "EchoSubscriptions=config");
                text = text.replace("NodeDirectoryEnabled=true", "NodeDirectoryEnabled=false");
                text = text.replace("JamBridgeEnabled=true", "JamBridgeEnabled=false");
            }
            Files.writeString(destination,
                              text,
                              StandardCharsets.UTF_8,
                              StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException alreadyCreated) {
            // A concurrent start must not replace an existing operator file.
        }
    }
}
