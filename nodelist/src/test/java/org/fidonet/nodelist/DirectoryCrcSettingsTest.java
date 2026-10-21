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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class DirectoryCrcSettingsTest {
    @TempDir
    Path root;

    private Path main;
    private Path fragment;

    private JFtnConfig config(String rules) throws Exception {
        main = root.resolve("jftn.conf");
        Files.writeString(main, "# Main settings are preserved\n");
        fragment = root.resolve("conf.d/30-nodelist.conf");
        Files.createDirectories(fragment.getParent());
        Files.writeString(fragment,
                          "# Operator comment\r\nNodeDirectoryEnabled=true\r\n"
                          + "NodeDirectoryPath="
                          + root.resolve("catalog")
                          + "\r\n"
                          + "DirectorySource=points|BOSS|"
                          + root.resolve("points.lst")
                          + "|0|0|UTF-8|true\r\n"
                          + rules);
        return JFtnConfig.bootstrap(main);
    }

    private Map<String, String> command(String mode) {
        return Map.of("source", "points", "mask", "pnt46reg.*", "mode", mode);
    }

    private Path publication() throws IOException {
        String body = ";S Created by New Point Keeper v.0.0.0.7.\nBoss,1:500/101\n"
                      + ",1,Point,City,Sysop,-Unpublished-,300\n";
        int crc = 0;
        for (byte value : body.getBytes(StandardCharsets.US_ASCII)) {
            crc = DirectoryParser.crc16Arc(crc, value);
        }
        return Files.writeString(root.resolve("opaque"), ";A Pointlist : " + crc + "\n" + body);
    }

    @Test
    void editsOwnedFragmentAppliesImmediatelyAndSurvivesRestart() throws Exception {
        JFtnConfig config = config("DirectoryIgnoreCRC=points|PNT46REG.*\r\n");
        String originalMain = Files.readString(main);
        Path incoming = publication();
        try (DirectoryService service = DirectoryService.open(config)) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            SettingsExtension.View view = settings.describe();
            assertEquals("nodelist", view.id);
            assertEquals("conf.d/30-nodelist.conf", view.source);
            assertTrue(view.sections.get(0).writable);
            assertEquals(List.of("XMODEM-CRC", "ARC-CRC"),
                         view.sections.get(0).fields.get(2).choices);
            assertEquals("CRC checks", view.sections.get(0).title);
            String definition = service.sourceDefinition("points");
            settings.apply("crc", command("ARC-CRC"));
            assertFalse(definition.equals(service.sourceDefinition("points")));
            String saved = Files.readString(fragment);
            assertTrue(saved.contains("# Operator comment\r\nNodeDirectoryEnabled=true\r\n"));
            assertTrue(saved.contains("DirectoryCRCCompatibility=points|PNT46REG.*|ARC-CRC\r\n"));
            assertFalse(saved.contains("DirectoryIgnoreCRC="));
            assertEquals(2, settings.describe().sections.size());
            assertEquals(originalMain, Files.readString(main));
            assertEquals(1,
                         service.importFile("points", incoming, "PNT46REG.289", false)
                                .getRecords());
        }
        try (DirectoryService service = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            assertFalse(service.importFile("points", incoming, "PNT46REG.289", false).isChanged());
            service.getSettingsExtensions().get(0).apply("crc", command("XMODEM-CRC"));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
        }
        try (DirectoryService service = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            assertEquals("XMODEM-CRC",
                         service.getSettingsExtensions()
                                .get(0)
                                .describe().sections.get(0).rows.get(0).get("mode"));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
        }
    }

    @Test
    void rejectsInvalidCommandsWithoutChangingFileOrLivePolicy() throws Exception {
        try (DirectoryService service = DirectoryService.open(config(""))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            String before = Files.readString(fragment);
            String definition = service.sourceDefinition("points");
            for (Map<String, String> command : List.of(command("ignore"),
                                                       Map.of("source",
                                                              "unknown",
                                                              "mask",
                                                              "PNT46REG.*",
                                                              "mode",
                                                              "XMODEM-CRC"),
                                                       Map.of("source",
                                                              "points",
                                                              "mask",
                                                              "*",
                                                              "mode",
                                                              "XMODEM-CRC"),
                                                       Map.of("source",
                                                              "points",
                                                              "mask",
                                                              "../PNT46REG.*",
                                                              "mode",
                                                              "ARC-CRC"),
                                                       Map.of("source",
                                                              "points",
                                                              "mask",
                                                              "PNT46REG.*",
                                                              "mode",
                                                              "XMODEM-CRC",
                                                              "extra",
                                                              "bad"))) {
                assertThrows(IllegalArgumentException.class, () -> settings.apply("crc", command));
                assertEquals(before, Files.readString(fragment));
                assertEquals(definition, service.sourceDefinition("points"));
            }
        }
    }

    @Test
    void refusesConfigurationInjectionBeforeBuildingAnyDeclarations() throws Exception {
        try (DirectoryService service = DirectoryService.open(config(""))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            String before = Files.readString(fragment);
            Map<String, String> injectedSource = Map.of("source",
                                                        "points|OTHER.*|ARC-CRC\nDirectoryCRCCompatibility=points",
                                                        "mask",
                                                        "PNT46REG.*",
                                                        "mode",
                                                        "ARC-CRC");
            Map<String, String> injectedMask = Map.of("source",
                                                      "points",
                                                      "mask",
                                                      "PNT46REG.*|ARC-CRC\nNodeDirectoryEnabled=false\n#",
                                                      "mode",
                                                      "ARC-CRC");
            assertThrows(IllegalArgumentException.class,
                         () -> settings.apply("crc", injectedSource));
            assertThrows(IllegalArgumentException.class, () -> settings.apply("crc", injectedMask));
            assertEquals(before, Files.readString(fragment));
        }
    }

    @Test
    void refusesToOverwriteExternalFileEditsAndKeepsTheLivePolicy() throws Exception {
        try (DirectoryService service = DirectoryService.open(config(""))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            String definition = service.sourceDefinition("points");
            String manual = Files.readString(fragment) + "# Manual change\n";
            Files.writeString(fragment, manual);
            assertFalse(settings.describe().sections.get(0).writable);
            assertThrows(IOException.class, () -> settings.apply("crc", command("ARC-CRC")));
            assertEquals(manual, Files.readString(fragment));
            assertEquals(definition, service.sourceDefinition("points"));
        }
    }

    @Test
    void explicitXmodemOverridesLegacyIgnoreButConflictingAlgorithmsAreRejected() throws Exception {
        try (DirectoryService service = DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT*.???\n"))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            settings.apply("crc", command("XMODEM-CRC"));
            Path incoming = publication();
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
            settings.apply("crc", command("ARC-CRC"));
            assertEquals(1,
                         service.importFile("points", incoming, "PNT46REG.289", false)
                                .getRecords());
            Map<String, String> conflict = Map.of("source",
                                                  "points",
                                                  "mask",
                                                  "PNT*.???",
                                                  "mode",
                                                  "XMODEM-CRC");
            String before = Files.readString(fragment);
            assertThrows(IllegalArgumentException.class, () -> settings.apply("crc", conflict));
            assertEquals(before, Files.readString(fragment));
        }
        assertTrue(DirectoryFilenameMasks.overlaps("PNT*.???", "pnt46reg.*"));
        assertTrue(DirectoryFilenameMasks.overlaps("P?T*", "PNT46REG.*"));
        assertFalse(DirectoryFilenameMasks.overlaps("NODELIST.*", "PNT46REG.*"));
    }

    @Test
    void oldUiModeValuesAreAcceptedAndSavedWithAlgorithmNames() throws Exception {
        try (DirectoryService service = DirectoryService.open(config("DirectoryCRCCompatibility=points|PNT46REG.*|new-point-keeper\n"))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            assertEquals("ARC-CRC", settings.describe().sections.get(0).rows.get(0).get("mode"));
            settings.apply("crc", command("standard"));
            assertTrue(Files.readString(fragment).contains("|XMODEM-CRC"));
            settings.apply("crc", command("new-point-keeper"));
            assertTrue(Files.readString(fragment).contains("|ARC-CRC"));
            assertFalse(Files.readString(fragment).contains("new-point-keeper"));
        }
    }

    @Test
    void rulesDeclaredOutsideTheOwnedFragmentAreReadOnly() throws Exception {
        config("");
        Path other = root.resolve("conf.d/99-custom.conf");
        Files.writeString(other, "DirectoryCRCCompatibility=points|PNT46REG.*|ARC-CRC\n");
        try (DirectoryService service = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            assertFalse(settings.describe().sections.get(0).writable);
            assertEquals(1, settings.describe().sections.get(0).rows.size());
            assertThrows(IOException.class, () -> settings.apply("crc", command("XMODEM-CRC")));
            assertTrue(Files.readString(other).contains("ARC-CRC"));
        }
    }
}
