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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DirectoryChecksumExceptionTest {
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
                      + "|0|0|UTF-8|true"
                      + "\nDirectorySource=other|BOSS|"
                      + root.resolve("other.lst")
                      + "|0|0|UTF-8|true\n"
                      + rules;
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private String list(String system, boolean correct) {
        String body = "Boss,1:500/101\r\n,1," + system + ",City,Sysop,-Unpublished-,300\r\n";
        int crc = 0;
        for (byte value : body.getBytes(StandardCharsets.UTF_8)) {
            crc = DirectoryParser.crc16(crc, value & 255);
        }
        return ";Pointlist : " + (correct ? crc : (crc ^ 1)) + "\r\n" + body;
    }

    private Path input(String text) throws IOException {
        return Files.writeString(root.resolve("opaque-received-payload"), text);
    }

    @Test
    void scopesExceptionToSourceAndOriginalFilenameAndRevalidatesWhenRemoved() throws Exception {
        Path incoming = input(list("Point", false));
        try (DirectoryService service = DirectoryService.open(config(""))) {
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
            assertFalse(Files.exists(root.resolve("points.lst")));
        }
        String strictDefinition;
        try (DirectoryService service = DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT46REG.*\n"))) {
            strictDefinition = service.sourceDefinition("points");
            assertThrows(IOException.class,
                         () -> service.importFile("other", incoming, "PNT46REG.289", false));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "OTHER.289", false));
            assertEquals(1,
                         service.importFile("points", incoming, "pnt46reg.289", false)
                                .getRecords());
            assertEquals("Point",
                         service.getCatalog()
                                .lookup(new FTNAddr("1:500/101.1"))
                                .get(0)
                                .getSystem());
            assertFalse(service.importFile("points", incoming, "PNT46REG.289", false).isChanged());
        }
        try (DirectoryService service = DirectoryService.open(config(""))) {
            assertFalse(strictDefinition.equals(service.sourceDefinition("points")));
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.289", false));
            assertEquals(1, service.getCatalog().lookup(new FTNAddr("1:500/101.1")).size());
        }
    }

    @Test
    void replaysAcrossRestartButStillRejectsBadStructureAndDuplicateAddresses() throws Exception {
        String rule = "DirectoryIgnoreCRC=points|PNT46REG.*\n";
        Path incoming = input(list("Original", false));
        try (DirectoryService service = DirectoryService.open(config(rule))) {
            service.importFile("points", incoming, "PNT46REG.289", false);
        }
        try (DirectoryService service = DirectoryService.open(config(rule))) {
            assertFalse(service.importFile("points", incoming, "PNT46REG.289", false).isChanged());
            String retained = Files.readString(root.resolve("points.lst"));
            for (String bad : new String[]{list("Changed", false).replace(",1,", ",70000,"),
                    list("Changed", false) + ",1,Duplicate,City,Sysop,-Unpublished-,300\r\n"}) {
                input(bad);
                assertThrows(IOException.class,
                             () -> service.importFile("points", incoming, "PNT46REG.290", false));
                assertEquals(retained, Files.readString(root.resolve("points.lst")));
                assertEquals("Original",
                             service.getCatalog()
                                    .lookup(new FTNAddr("1:500/101.1"))
                                    .get(0)
                                    .getSystem());
            }
        }
    }

    @Test
    void neverIgnoresBaseOrResultChecksumsForDiffs() throws Exception {
        try (DirectoryService service = DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT46REG.*\n"))) {
            String bad = list("Bad", false);
            Path incoming = input(bad);
            service.importFile("points", incoming, "PNT46REG.289", false);
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.290", true));
            String good = list("Good", true);
            input(good);
            service.importFile("points", incoming, "PNT46REG.291", false);
            input(good.substring(0, good.indexOf('\r')) + "\r\nD3\r\nA3\r\n" + bad);
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "PNT46REG.292", true));
            assertEquals(good, Files.readString(root.resolve("points.lst")));
        }
    }

    @Test
    void supportsMatchingFullZipButDoesNotBypassArchiveValidation() throws Exception {
        Path zip = root.resolve("delivery.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("PNT46REG.289"));
            output.write(list("Archived", false).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (DirectoryService service = DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT46REG.*\n"))) {
            assertEquals(1, service.importFile("points", zip, "PNT46REG.Z89", false).getRecords());
            Files.write(zip, new byte[]{'P', 'K', 3, 4});
            assertThrows(IOException.class,
                         () -> service.importFile("points", zip, "PNT46REG.Z90", false));
            assertEquals("Archived",
                         service.getCatalog()
                                .lookup(new FTNAddr("1:500/101.1"))
                                .get(0)
                                .getSystem());
        }
    }

    @Test
    void rejectsUnscopedUnknownAndPathMasksBeforeOpeningCatalog() throws Exception {
        for (String rule : new String[]{"true", "unknown|PNT46REG.*", "points|*",
                "points|../PNT46REG.*"}) {
            assertThrows(IllegalArgumentException.class,
                         () -> DirectoryService.open(config("DirectoryIgnoreCRC=" + rule + "\n")));
        }
        assertFalse(Files.exists(root.resolve("catalog.mv.db")));
    }

    @Test
    void rejectsDuplicateMasksIgnoringCaseBeforeOpeningCatalog() throws Exception {
        assertThrows(IllegalArgumentException.class,
                     () -> DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT46REG.*\n"
                             + "DirectoryIgnoreCRC=points|pnt46reg.*\n")));
        assertFalse(Files.exists(root.resolve("catalog.mv.db")));
    }

    @Test
    void missingHeaderChecksumIsExemptedOnlyForMatchingName() throws Exception {
        String body = list("Point", true);
        Path incoming = input(";Pointlist without CRC\r\n"
                              + body.substring(body.indexOf('\n') + 1));
        try (DirectoryService service = DirectoryService.open(config("DirectoryIgnoreCRC=points|PNT46REG.*\n"))) {
            assertThrows(IOException.class,
                         () -> service.importFile("points", incoming, "OTHER.289", false));
            assertEquals(1,
                         service.importFile("points", incoming, "PNT46REG.289", false)
                                .getRecords());
        }
    }
}
