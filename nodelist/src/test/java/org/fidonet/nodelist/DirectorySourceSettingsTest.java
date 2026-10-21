package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.plugins.ui.SettingsExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DirectorySourceSettingsTest {
    @TempDir
    Path root;
    private Path main;
    private Path fragment;

    private JFtnConfig config() throws Exception {
        main = root.resolve("jftn.conf");
        fragment = root.resolve("conf.d/30-nodelist.conf");
        Files.createDirectories(fragment.getParent());
        Files.writeString(main, "# Main file stays untouched\n");
        Files.writeString(fragment,
                          "# Keep this comment\r\nNodeDirectoryEnabled=true\r\n"
                          + "NodeDirectoryPath="
                          + root.resolve("catalog")
                          + "\r\n"
                          + "DirectoryLocalSource=nodes\r\n"
                          + "DirectorySource=nodes|NODELIST|"
                          + root.resolve("nodes.lst")
                          + "|1|500|US-ASCII|false\r\n"
                          + "DirectorySource=points|BOSS|"
                          + root.resolve("points.lst")
                          + "|0|0|UTF-8|true\r\n");
        return JFtnConfig.bootstrap(main);
    }

    private Map<String, String> command(String id, Path path) {
        return Map.of("id",
                      id,
                      "format",
                      "NODELIST",
                      "path",
                      path.toString(),
                      "zone",
                      "1",
                      "net",
                      "500",
                      "charset",
                      "KOI8-R",
                      "requireChecksum",
                      "false");
    }

    private SettingsExtension.Section sources(SettingsExtension settings) throws IOException {
        return settings.describe().sections.stream()
                                           .filter(section -> section.id.equals("sources"))
                                           .findFirst()
                                           .orElseThrow();
    }

    @Test
    void persistsAndPublishesEditsKeepsLocalRefreshAndAllowsCrcEditsAfterwards() throws Exception {
        JFtnConfig config = config();
        String originalMain = Files.readString(main);
        Path publication = root.resolve("new-nodes.lst");
        Files.writeString(publication,
                          ",101,Станция,City,Sysop,-Unpublished-,300\n",
                          Charset.forName("KOI8-R"));
        String originalList = Files.readString(publication, Charset.forName("KOI8-R"));
        try (DirectoryService service = DirectoryService.open(config)) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            assertTrue(sources(settings).writable);
            settings.apply("sources", command("nodes", publication));
            assertEquals(publication, service.sourcePath("nodes"));
            assertEquals(1, service.refreshLocal().get(0).getRecords());
            assertEquals(1, service.getCatalog().search("Станция", 0, 10).size());
            settings.apply("crc",
                           Map.of("source", "points", "mask", "POINTS.*", "mode", "XMODEM-CRC"));
            settings.apply("sources", command("another", root.resolve("future.lst")));
            assertTrue(service.hasSource("another"));
            assertEquals(originalList, Files.readString(publication, Charset.forName("KOI8-R")));
            assertEquals(originalMain, Files.readString(main));
            assertTrue(Files.readString(fragment).contains("# Keep this comment\r\n"));
            assertTrue(Files.readString(fragment)
                            .contains("DirectoryCRCCompatibility=points|POINTS.*|XMODEM-CRC"));
        }
        try (DirectoryService restarted = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            assertEquals(publication, restarted.sourcePath("nodes"));
            assertTrue(restarted.hasSource("another"));
            assertEquals(1, restarted.getCatalog().search("Станция", 0, 10).size());
        }
    }

    @Test
    void invalidCommandsAndConflictingPathsLeaveDiskAndRuntimeUnchanged() throws Exception {
        try (DirectoryService service = DirectoryService.open(config())) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            String original = Files.readString(fragment);
            String definition = service.sourceDefinition("nodes");
            for (Map.Entry<String, String> invalid : List.of(Map.entry("format", "GUESS"),
                                                             Map.entry("charset",
                                                                       "unknown-charset"),
                                                             Map.entry("zone", "65536"),
                                                             Map.entry("net", "-1"),
                                                             Map.entry("requireChecksum", "maybe"),
                                                             Map.entry("path",
                                                                       "other.lst\nNodeDirectoryEnabled=false"),
                                                             Map.entry("path", "other.lst#comment"),
                                                             Map.entry("id", "bad|id"))) {
                Map<String, String> command = new HashMap<>(command("nodes",
                                                                    root.resolve("next.lst")));
                command.put(invalid.getKey(), invalid.getValue());
                assertThrows(IllegalArgumentException.class,
                             () -> settings.apply("sources", command));
                assertEquals(original, Files.readString(fragment));
                assertEquals(definition, service.sourceDefinition("nodes"));
            }
            assertThrows(IllegalArgumentException.class,
                         () -> settings.apply("sources",
                                              command("another", root.resolve("points.lst"))));
            assertThrows(IllegalArgumentException.class,
                         () -> settings.apply("sources", Map.of("id", "nodes")));
            assertEquals(original, Files.readString(fragment));
            assertFalse(service.hasSource("another"));
        }
    }

    @Test
    void externalEditsAndDeliveryManagedPathChangesAreRejectedWithoutPublishing() throws Exception {
        try (DirectoryService service = DirectoryService.open(config())) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            service.claimDeliverySource("points");
            String before = Files.readString(fragment);
            assertThrows(IllegalArgumentException.class,
                         () -> settings.apply("sources",
                                              command("points", root.resolve("other.lst"))));
            assertEquals(before, Files.readString(fragment));
            Files.writeString(fragment, before + "# Edited outside RPC\r\n");
            assertFalse(sources(settings).writable);
            assertThrows(IOException.class,
                         () -> settings.apply("sources",
                                              command("nodes", root.resolve("other.lst"))));
            assertEquals(root.resolve("nodes.lst"), service.sourcePath("nodes"));
        }
    }
}
