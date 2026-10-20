package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.storage.StorageSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class OfflineStorageEndpointTest {
    @TempDir
    Path root;

    @Test
    void offlineOperationsUseTheConfiguredMailEndpointAndRefuseMissingState() throws Exception {
        Path location = root.resolve("selected-mail");
        JFtnConfig config = config("h2", location.toString());
        assertThrows(IOException.class, () -> OfflineMailStorage.open(config, true));
        assertFalse(Files.exists(Path.of(location + ".mv.db")));
        try (StorageSession storage = OfflineMailStorage.open(config, false)) {
            assertTrue(OfflineMailStorage.repository(storage).createArea("selected.echo"));
        }
        try (StorageSession storage = OfflineMailStorage.open(config, true)) {
            assertTrue(OfflineMailStorage.repository(storage).isAreaExists("selected.echo"));
        }
    }

    @Test
    void offlineOperationsRejectAnUnavailableProviderWithoutFallingBack() throws Exception {
        JFtnConfig config = config("unavailable", root.resolve("unused").toString());
        assertThrows(IOException.class, () -> OfflineMailStorage.open(config, false));
        try (var files = Files.list(root)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    private JFtnConfig config(String provider, String location) throws Exception {
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nLink=1:500/101,1:500/100,password,localhost\nStorageEndpoint=mail|"
                      + provider
                      + "|"
                      + location
                      + "\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }
}
