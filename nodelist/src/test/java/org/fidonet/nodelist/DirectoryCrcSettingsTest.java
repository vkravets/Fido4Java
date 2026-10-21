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
            assertEquals(List.of("standard", "new-point-keeper"),
                         view.sections.get(0).fields.get(2).choices);
            assertEquals("Перевірка CRC", view.translations.get("uk").get("CRC checks"));
            String definition = service.sourceDefinition("points");
            settings.apply("crc", command("new-point-keeper"));
            assertFalse(definition.equals(service.sourceDefinition("points")));
            String saved = Files.readString(fragment);
            assertTrue(saved.contains("# Operator comment\r\nNodeDirectoryEnabled=true\r\n"));
            assertTrue(saved.contains("DirectoryCRCCompatibility=points|PNT46REG.*|new-point-keeper\r\n"));
            assertFalse(saved.contains("DirectoryIgnoreCRC="));
            assertEquals(1, settings.describe().sections.size());
            assertEquals(originalMain, Files.readString(main));
            assertEquals(1,
                         service.importFile("points", incoming, "PNT46REG.289", false)
                                .getRecords());
        }
        try (DirectoryService service = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            assertFalse(service.importFile("points", incoming, "PNT46REG.289", false).isChanged());
            service.getSettingsExtensions().get(0).apply("crc", command("standard"));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
        }
        try (DirectoryService service = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            assertTrue(service.getSettingsExtensions()
                              .get(0)
                              .describe().sections.get(0).rows.isEmpty());
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
                                                              "standard"),
                                                       Map.of("source",
                                                              "points",
                                                              "mask",
                                                              "*",
                                                              "mode",
                                                              "standard"),
                                                       Map.of("source",
                                                              "points",
                                                              "mask",
                                                              "../PNT46REG.*",
                                                              "mode",
                                                              "new-point-keeper"),
                                                       Map.of("source",
                                                              "points",
                                                              "mask",
                                                              "PNT46REG.*",
                                                              "mode",
                                                              "standard",
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
                                                        "points|OTHER.*|new-point-keeper\nDirectoryCRCCompatibility=points",
                                                        "mask",
                                                        "PNT46REG.*",
                                                        "mode",
                                                        "new-point-keeper");
            Map<String, String> injectedMask = Map.of("source",
                                                      "points",
                                                      "mask",
                                                      "PNT46REG.*|new-point-keeper\nNodeDirectoryEnabled=false\n#",
                                                      "mode",
                                                      "new-point-keeper");
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
            assertThrows(IOException.class,
                         () -> settings.apply("crc", command("new-point-keeper")));
            assertEquals(manual, Files.readString(fragment));
            assertEquals(definition, service.sourceDefinition("points"));
        }
    }

    @Test
    void standardModeCannotSilentlyFallBackToAnotherOverlappingException() throws Exception {
        try (DirectoryService service = DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT*.???\n"))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            String before = Files.readString(fragment);
            assertThrows(IllegalArgumentException.class,
                         () -> settings.apply("crc", command("standard")));
            assertEquals(before, Files.readString(fragment));
            settings.apply("crc", command("new-point-keeper"));
            Path incoming = publication();
            Files.writeString(incoming,
                              Files.readString(incoming).replace("Point,City", "Changed,City"));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
        }
        assertTrue(DirectoryCrcSettings.overlaps("PNT*.???", "pnt46reg.*"));
        assertTrue(DirectoryCrcSettings.overlaps("P?T*", "PNT46REG.*"));
        assertFalse(DirectoryCrcSettings.overlaps("NODELIST.*", "PNT46REG.*"));
    }

    @Test
    void rulesDeclaredOutsideTheOwnedFragmentAreReadOnly() throws Exception {
        config("");
        Path other = root.resolve("conf.d/99-custom.conf");
        Files.writeString(other, "DirectoryCRCCompatibility=points|PNT46REG.*|new-point-keeper\n");
        try (DirectoryService service = DirectoryService.open(JFtnConfig.bootstrap(main))) {
            SettingsExtension settings = service.getSettingsExtensions().get(0);
            assertFalse(settings.describe().sections.get(0).writable);
            assertEquals(1, settings.describe().sections.get(0).rows.size());
            assertThrows(IOException.class, () -> settings.apply("crc", command("standard")));
            assertTrue(Files.readString(other).contains("new-point-keeper"));
        }
    }
}
