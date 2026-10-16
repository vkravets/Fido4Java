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
    void installedPluginFilesOnceAndRecreatedOnlyMissingTemplates() throws Exception {
        NodeDirectories.defaults(root);
        for (String name : PluginConfigurationFiles.NAMES) {
            assertTrue(Files.isRegularFile(root.resolve("conf.d").resolve(name)));
        }
        Path manual = root.resolve("conf.d/20-jam-bridge.conf");
        Files.writeString(manual, "# local choice\nJamBridgeEnabled=false\n");
        Files.delete(root.resolve("conf.d/30-nodelist.conf"));
        NodeDirectories.defaults(root);
        assertEquals("# local choice\nJamBridgeEnabled=false\n", Files.readString(manual));
        assertTrue(Files.isRegularFile(root.resolve("conf.d/30-nodelist.conf")));
    }

    @Test
    void loadedPluginListsFromSeparateFilesInHandlerOrder() throws Exception {
        NodeDirectories.defaults(root);
        Files.writeString(root.resolve("conf.d/40-mail-scripts.conf"),
                          "MailScript=classpath:mail-scripts/filefix.groovy\nMailScript=classpath:mail-scripts/directory-import.groovy\n");
        Files.writeString(root.resolve("conf.d/42-filefix.conf"),
                          "FileArea=lists|./fileecho/lists|Lists\n");
        Path main = Files.writeString(root.resolve("jftn.conf"),
                                      "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n");
        JFtnConfig config = new JFtnConfig();
        config.load(main.toString());
        assertEquals(List.of("classpath:mail-scripts/filefix.groovy",
                             "classpath:mail-scripts/directory-import.groovy"),
                     config.getValuesAsList("MailScript"));
        assertEquals("lists|./fileecho/lists|Lists", config.getValue("FileArea"));
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
