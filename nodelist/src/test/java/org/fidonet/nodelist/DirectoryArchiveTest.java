package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DirectoryArchiveTest {
    @TempDir
    Path temporary;

    private DirectorySource source(Path path) {
        return new DirectorySource("nodes",
                                   DirectorySource.Format.NODELIST,
                                   path,
                                   1,
                                   500,
                                   StandardCharsets.US_ASCII,
                                   false);
    }

    private Path archive(Map<String, String> members) throws IOException {
        Path path = temporary.resolve("NODELIST.Z88");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> member : members.entrySet()) {
                zip.putNextEntry(new ZipEntry(member.getKey()));
                zip.write(member.getValue().getBytes(StandardCharsets.US_ASCII));
                zip.closeEntry();
            }
        }
        return path;
    }

    @Test
    void importsZipBySignatureWithoutExtractingItsMemberPaths() throws Exception {
        Path archive = archive(Map.of("../../escape/NODELIST.288",
                                      DirectoryParserTest.checked(DirectoryParserTest.row("",
                                                                                          101,
                                                                                          "Zipped"))));
        try (DirectoryCatalog catalog = new DirectoryCatalog(temporary.resolve("catalog"))) {
            DirectorySource source = source(archive);
            assertEquals(1, catalog.importSource(source).getRecords());
            assertFalse(catalog.importSource(source).isChanged());
            assertEquals("Zipped",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
        }
        assertFalse(Files.exists(temporary.resolve("escape")));
    }

    @Test
    void rejectsAmbiguousEmptyAndInvalidArchivesWithoutReplacingThePreviousList() throws Exception {
        Path archive = archive(Map.of("NODELIST.288",
                                      DirectoryParserTest.row("", 101, "Original")));
        DirectorySource source = source(archive);
        try (DirectoryCatalog catalog = new DirectoryCatalog(temporary.resolve("catalog"))) {
            catalog.importSource(source);
            archive(Map.of("one", DirectoryParserTest.row("", 102, "New"), "two", "unexpected"));
            assertThrows(IOException.class, () -> catalog.importSource(source));
            archive(Map.of());
            assertThrows(IOException.class, () -> catalog.importSource(source));
            archive(Map.of("NODELIST.288",
                           ";A day 288 : 00000\r\n" + DirectoryParserTest.row("", 102, "Bad")));
            assertThrows(IOException.class, () -> catalog.importSource(source));
            assertEquals("Original",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
        }
    }
}
