package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.BaseConfig;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DirectoryCrcCompatibilityTest {
    private static final String MARKER = ";S Created by New Point Keeper v.0.0.0.7.";
    private static final String RULE = "DirectoryCRCCompatibility=points|PNT46REG.*|ARC-CRC\n";
    @TempDir
    Path root;

    private BaseConfig config(String rules) throws Exception {
        BaseConfig config = new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
        String text = "NodeDirectoryPath="
                      + root.resolve("catalog")
                      + "\nDirectorySource=points|BOSS|"
                      + root.resolve("points.lst")
                      + "|0|0|UTF-8|true\nDirectorySource=other|BOSS|"
                      + root.resolve("other.lst")
                      + "|0|0|UTF-8|true\n"
                      + rules;
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private String publication(String body, boolean arc) {
        int crc = 0;
        for (byte value : body.getBytes(StandardCharsets.UTF_8)) {
            crc = arc
                    ? DirectoryParser.crc16Arc(crc, value & 255)
                    : DirectoryParser.crc16(crc, value & 255);
        }
        return ";A Pointlist : " + crc + "\n" + body;
    }

    private String body(String system) {
        return MARKER
               + "\n;S Checked against example nodelist\n;\nBoss,1:500/101\n,1,"
               + system
               + ",City,Sysop,-Unpublished-,300\n";
    }

    private Path input(String text) throws IOException {
        return Files.writeString(root.resolve("opaque-input"), text);
    }

    @Test
    void arcCheckVectorMatchesTheIndependentCatalogue() {
        int crc = 0;
        for (byte value : "123456789".getBytes(StandardCharsets.US_ASCII)) {
            crc = DirectoryParser.crc16Arc(crc, value);
        }
        assertEquals(0xbb3d, crc);
    }

    @Test
    void scopesCompatibilityAndKeepsOriginalBytesAcrossRestart() throws Exception {
        String text = publication(body("Point"), true);
        Path incoming = input(text);
        try (DirectoryService strict = DirectoryService.open(config(""))) {
            assertThrows(IOException.class,
                         () -> strict.importFile("points", incoming, "PNT46REG.289", false));
        }
        String definition;
        try (DirectoryService service = DirectoryService.open(config(RULE))) {
            definition = service.sourceDefinition("points");
            assertThrows(IOException.class,
                         () -> service.importFile("other", incoming, "PNT46REG.289", false));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "other.289", false));
            assertEquals(1,
                         service.importFile("points", incoming, "pnt46reg.289", false)
                                .getRecords());
            assertEquals(text, Files.readString(root.resolve("points.lst")));
        }
        try (DirectoryService service = DirectoryService.open(config(RULE))) {
            assertFalse(service.importFile("points", incoming, "PNT46REG.289", false).isChanged());
            assertEquals("Point",
                         service.getCatalog()
                                .lookup(new FTNAddr("1:500/101.1"))
                                .get(0)
                                .getSystem());
        }
        try (DirectoryService strict = DirectoryService.open(config(""))) {
            assertFalse(definition.equals(strict.sourceDefinition("points")));
            assertThrows(IOException.class,
                         () -> strict.importFile("points", incoming, "PNT46REG.289", false));
        }
    }

    @Test
    void selectedAlgorithmNeverSkipsValidationEvenWithAnOldIgnoreRule() throws Exception {
        String original = publication(body("Original"), true);
        Path incoming = input(original);
        try (DirectoryService service = DirectoryService.open(config(RULE
                + "DirectoryIgnoreCRC=points|PNT46REG.*\n"))) {
            service.importFile("points", incoming, "PNT46REG.289", false);
            for (String bad : List.of(original.replace("Original", "Tampered"),
                                      original.replace("v.0.0.0.7.", "v.0.0.0.8."),
                                      original.replaceAll(": \\d+\\n", " : \n"),
                                      original.replace("\n", "\r\n"),
                                      publication(body("Invalid").replace(",1,", ",70000,"), true),
                                      publication(body("Duplicate")
                                              + ",1,Other,City,Sysop,-Unpublished-,300\n", true))) {
                input(bad);
                assertThrows(IOException.class,
                             () -> service.importFile("points", incoming, "PNT46REG.290", false));
                assertEquals(original, Files.readString(root.resolve("points.lst")));
                assertEquals("Original",
                             service.getCatalog()
                                    .lookup(new FTNAddr("1:500/101.1"))
                                    .get(0)
                                    .getSystem());
            }
        }
    }

    @Test
    void selectedArcUsesActualTerminatorsAndDoesNotFallBackToXmodem() throws Exception {
        try (DirectoryService service = DirectoryService.open(config(RULE))) {
            for (String body : List.of(body("LF"),
                                       body("CRLF").replace("\n", "\r\n"),
                                       body("NoFinalNewline").stripTrailing())) {
                Path incoming = input(publication(body, true) + "\u001a");
                assertEquals(1,
                             service.importFile("points", incoming, "PNT46REG.289", false)
                                    .getRecords());
            }
            String standard = publication(body("Standard").replace(MARKER, ";Different program")
                                                          .replace("\n", "\r\n"),
                                          false);
            Path standardFile = input(standard);
            assertThrows(IOException.class,
                         () -> service.importFile("points", standardFile, "PNT46REG.290", false));
        }
        try (DirectoryService service = DirectoryService.open(config(RULE.replace("ARC-CRC",
                                                                                  "XMODEM-CRC")))) {
            Path arcFile = input(publication(body("Arc"), true));
            assertThrows(IOException.class,
                         () -> service.importFile("points", arcFile, "PNT46REG.289", false));
            Path standardFile = input(publication(body("Standard").replace("\n", "\r\n"), false));
            assertTrue(service.importFile("points", standardFile, "PNT46REG.290", false)
                              .isChanged());
        }
    }

    @Test
    void arcDoesNotDependOnGeneratorNameOrVersionAndReadsTheLegacyAlias() throws Exception {
        String alias = RULE.replace("ARC-CRC", "new-point-keeper");
        try (DirectoryService service = DirectoryService.open(config(alias))) {
            for (String text : List.of(body("Other").replace(MARKER,
                                                             ";Created by another application"),
                                       body("Version").replace("v.0.0.0.7.", "v.9.0."),
                                       body("NoMarker").replace(MARKER + "\n", ""))) {
                assertEquals(1,
                             service.importFile("points",
                                                input(publication(text, true)),
                                                "PNT46REG.289",
                                                false)
                                    .getRecords());
            }
        }
    }

    @Test
    void zippedFullListsUseOuterFilenameAndDiffBasesStayStrict() throws Exception {
        String text = publication(body("Archived"), true);
        Path archive = root.resolve("opaque.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("different-member-name"));
            zip.write(text.getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        try (DirectoryService service = DirectoryService.open(config(RULE))) {
            assertEquals(1,
                         service.importFile("points", archive, "PNT46REG.Z89", false).getRecords());
            Path diff = input(text.lines().findFirst().orElseThrow() + "\nC1\n");
            assertThrows(IOException.class,
                         () -> service.importFile("points", diff, "PNT46REG.290", true));
            assertEquals(text, Files.readString(root.resolve("points.lst")));
        }
        DirectorySource opted = DirectorySource.parse("points|BOSS|"
                                                      + root.resolve("points.lst")
                                                      + "|0|0|UTF-8|true").withArcChecksum(true);
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("direct-catalog"))) {
            assertThrows(IOException.class,
                         () -> DirectoryUpdate.install(catalog, opted, input(text), true));
        }
    }

    @Test
    void localRefreshMatchesTheRetainedBasenameRatherThanTheDeliveryName() throws Exception {
        Files.writeString(root.resolve("points.lst"), publication(body("Local"), true));
        String local = "DirectoryLocalSource=points\nDirectoryRefreshEnabled=true\n";
        try (DirectoryService service = DirectoryService.open(config(RULE + local))) {
            assertThrows(IOException.class, service::refreshLocal);
        }
        String rule = "DirectoryCRCCompatibility=points|points.lst|ARC-CRC\n";
        try (DirectoryService service = DirectoryService.open(config(rule + local))) {
            assertEquals(1, service.refreshLocal().get(0).getRecords());
        }
    }

    @Test
    void explicitXmodemRequiresAChecksumEvenForAnOtherwiseOptionalSource() throws Exception {
        DirectorySource optional = DirectorySource.parse("points|BOSS|"
                                                         + root.resolve("points.lst")
                                                         + "|0|0|UTF-8|false");
        DirectoryChecksumExceptions policy = new DirectoryChecksumExceptions(config(RULE.replace("ARC-CRC",
                                                                                                 "XMODEM-CRC")),
                                                                             Set.of("points"));
        DirectorySource selected = policy.select(optional, "PNT46REG.289", false);
        byte[] bytes = (";A Pointlist without CRC\n"
                + body("Optional")).getBytes(StandardCharsets.UTF_8);
        assertEquals(1, DirectoryParser.parse(new ByteArrayInputStream(bytes), optional, entry -> {
        }).getRecords());
        assertThrows(IOException.class,
                     () -> DirectoryParser.parse(new ByteArrayInputStream(bytes),
                                                 selected,
                                                 entry -> {
                                                 }));
    }

    @Test
    void rejectsInvalidRulesBeforeOpeningStorage() throws Exception {
        for (String rule : List.of("unknown|PNT46REG.*|ARC-CRC",
                                   "points|*|ARC-CRC",
                                   "points|../PNT46REG.*|ARC-CRC",
                                   "points|PNT46REG.*|auto",
                                   "true")) {
            assertThrows(IllegalArgumentException.class,
                         () -> DirectoryService.open(config("DirectoryCRCCompatibility="
                                                            + rule
                                                            + "\n")));
        }
        assertThrows(IllegalArgumentException.class,
                     () -> DirectoryService.open(config(RULE
                                                        + RULE.replace("PNT46REG", "pnt46reg"))));
        assertFalse(Files.exists(root.resolve("catalog.mv.db")));
    }
}
