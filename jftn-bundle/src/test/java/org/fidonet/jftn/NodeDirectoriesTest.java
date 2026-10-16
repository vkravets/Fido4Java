package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class NodeDirectoriesTest {
    @TempDir
    Path root;

    @Test
    void recreatedMissingFoldersWithoutChangingExistingData() throws Exception {
        NodeDirectories.defaults(root);
        Path stored = root.resolve("mail/inbound/message.pkt");
        Files.writeString(stored, "retained");
        Files.delete(root.resolve("mail/outbound"));
        NodeDirectories.defaults(root);
        assertTrue(Files.isDirectory(root.resolve("mail/outbound")));
        assertEquals("retained", Files.readString(stored));
    }

    @Test
    void createdConfiguredPathsAndRejectedAFileInPlaceOfDirectory() throws Exception {
        StringBuilder text = new StringBuilder("SysOp=Test\nLink=1:500/101,1:500/100,-\n");
        for (String key : List.of("Inbound", "Outbound", "Tmp", "EchoPath")) {
            text.append(key).append('=').append(root.resolve("custom/" + key)).append('\n');
        }
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)));
        NodeDirectories.configured(config);
        assertTrue(Files.isDirectory(root.resolve("custom/Outbound")));
        Files.delete(root.resolve("custom/Inbound"));
        Files.writeString(root.resolve("custom/Inbound"), "blocked");
        assertThrows(IOException.class, () -> NodeDirectories.configured(config));
    }
}
