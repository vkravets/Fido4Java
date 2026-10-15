package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.BaseConfig;
import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

class DirectoryPluginTest {
    @TempDir
    Path temporary;

    private JFtnConfig configuration(String source, String enabled) {
        return new JFtnConfig() {
            @Override
            public String getValue(String key, String fallback) {
                if (key.equals("NodeDirectoryEnabled")) {
                    return enabled;
                }
                if (key.equals("NodeDirectoryPath")) {
                    return temporary.resolve("catalog").toString();
                }
                return fallback;
            }

            @Override
            public List<String> getValuesAsList(String key) {
                return List.of(source);
            }
        };
    }

    @Test
    void discoversBothSpisAndClearsTheFactoryContext() {
        assertTrue(ServiceLoader.load(Plugin.class)
                                .stream()
                                .anyMatch(provider -> provider.type() == DirectoryPlugin.class));
        assertTrue(ServiceLoader.load(MailExtensionFactory.class)
                                .stream()
                                .anyMatch(provider -> provider.type() == DirectoryPlugin.class));
        DirectoryPlugin plugin = new DirectoryPlugin();
        plugin.load();
        assertSame(plugin, plugin.getContext().get());
        plugin.unload();
        assertNull(plugin.getContext().get());
    }

    @Test
    void disabledPluginDoesNotInspectAnyFilesOrCreateADatabase() {
        JFtnConfig config = new JFtnConfig() {
            @Override
            public String getValue(String key, String fallback) {
                if (!key.equals("NodeDirectoryEnabled")) {
                    throw new AssertionError("Disabled plugin read " + key);
                }
                return "false";
            }
        };
        assertTrue(new DirectoryPlugin().create(config, null).isEmpty());
        assertThrows(IllegalArgumentException.class,
                     () -> new DirectoryPlugin().create(configuration("bad", "tru"), null));
        assertFalse(Files.exists(temporary.resolve("catalog.mv.db")));
    }

    @Test
    void importsAtCreationAndClosesTheServiceWithItsOwner() throws Exception {
        Path file = temporary.resolve("nodes.lst");
        Files.writeString(file, DirectoryParserTest.row("", 101, "Test"));
        JFtnConfig config = configuration("nodes|NODELIST|" + file + "|1|500", "true");
        DirectoryService service;
        try (MailProcessingExtension extension = new DirectoryPlugin().create(config, null)
                                                                      .orElseThrow()) {
            service = (DirectoryService) extension;
            assertEquals("Test",
                         service.getCatalog()
                                .lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
            assertFalse(service.refresh().get(0).isChanged());
        }
        assertThrows(IOException.class,
                     () -> service.getCatalog().lookup(new FTNAddr("1:500/101")));
    }

    @Test
    void offlineCommandReadsDirectoryOnlyConfigAndDoesNotRequireMailSettings() throws Exception {
        Path file = temporary.resolve("nodes.lst");
        Files.writeString(file, DirectoryParserTest.row("", 101, "Offline"));
        Path config = temporary.resolve("directory.conf");
        Files.writeString(config,
                          "NodeDirectoryPath="
                          + temporary.resolve("catalog")
                          + "\nDirectorySource=nodes|NODELIST|"
                          + file
                          + "|1|500\n");
        DirectoryImport.main(new String[]{config.toString()});
        try (DirectoryCatalog catalog = new DirectoryCatalog(temporary.resolve("catalog"))) {
            assertEquals("Offline",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
        }
        assertFalse(Files.exists(temporary.resolve("outbound")));
    }

    @Test
    void validatesSourceDefinitionsBeforeOpeningStorage() throws Exception {
        BaseConfig config = new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
        config.load(new ByteArrayInputStream("DirectorySource=duplicate|BOSS|one\nDirectorySource=duplicate|BOSS|two\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> DirectoryService.open(config));
        for (String value : List.of("bad",
                                    "id|NODELIST|x|0|0|UTF-8|treu",
                                    "id|UNKNOWN|x",
                                    "id|BOSS||1")) {
            assertThrows(IllegalArgumentException.class, () -> DirectorySource.parse(value));
        }
    }
}
