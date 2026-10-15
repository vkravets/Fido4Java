package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

class DirectoryCatalogTest {
    @TempDir
    Path temporary;

    private DirectorySource write(String id, String text) throws IOException {
        Path file = temporary.resolve(id + ".lst");
        Files.writeString(file, text, StandardCharsets.US_ASCII);
        return new DirectorySource(id,
                                   DirectorySource.Format.NODELIST,
                                   file,
                                   1,
                                   500,
                                   StandardCharsets.US_ASCII,
                                   false);
    }

    private DirectoryCatalog catalog() throws IOException {
        return new DirectoryCatalog(temporary.resolve("db/catalog"));
    }

    @Test
    void persistsRecordsAndFingerprintsAcrossRestartAndDetectsSameSizeEdits() throws Exception {
        DirectorySource source = write("nodes", DirectoryParserTest.row("", 101, "NodeA"));
        DirectoryImportResult initial;
        try (DirectoryCatalog catalog = catalog()) {
            initial = catalog.importSource(source);
            assertTrue(initial.isChanged());
            assertEquals(1, initial.getRecords());
        }
        try (DirectoryCatalog reopened = catalog()) {
            assertFalse(reopened.importSource(source).isChanged());
            FileTime modified = Files.getLastModifiedTime(source.getPath());
            Files.writeString(source.getPath(), DirectoryParserTest.row("", 101, "NodeB"));
            Files.setLastModifiedTime(source.getPath(), modified);
            DirectoryImportResult changed = reopened.importSource(source);
            assertTrue(changed.isChanged());
            assertFalse(initial.getFingerprint().equals(changed.getFingerprint()));
            DirectoryEntry entry = reopened.lookup("nodes", new FTNAddr("1:500/101")).orElseThrow();
            assertEquals("NodeB", entry.getSystem());
            assertEquals("Jane Doe", entry.getSysop());
            assertEquals(1, entry.getLine());
        }
    }

    @Test
    void atomicallyReplacesOnlyOneSourceAndPreservesItOnDuplicatesAndBadCrc() throws Exception {
        DirectorySource first = write("nodes", DirectoryParserTest.row("", 101, "Old"));
        DirectorySource second = write("other", DirectoryParserTest.row("", 101, "Other"));
        try (DirectoryCatalog catalog = catalog()) {
            catalog.importSource(first);
            catalog.importSource(second);
            assertEquals(2, catalog.lookup(new FTNAddr("1:500/101")).size());
            Files.writeString(first.getPath(),
                              DirectoryParserTest.row("", 102, "New")
                                               + DirectoryParserTest.row("", 102, "Duplicate"));
            assertThrows(IOException.class, () -> catalog.importSource(first));
            assertEquals("Old",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
            assertTrue(catalog.lookup("nodes", new FTNAddr("1:500/102")).isEmpty());
            Files.writeString(first.getPath(),
                              ";A day 288 : 00000\r\n"
                              + DirectoryParserTest.row("", 102, "BadCRC"));
            assertThrows(IOException.class, () -> catalog.importSource(first));
            assertEquals(2, catalog.lookup(new FTNAddr("1:500/101")).size());
            Files.writeString(first.getPath(), DirectoryParserTest.row("", 102, "New"));
            catalog.importSource(first);
            assertTrue(catalog.lookup("nodes", new FTNAddr("1:500/101")).isEmpty());
            assertEquals(1, catalog.lookup(new FTNAddr("1:500/101")).size());
            catalog.removeSource("nodes");
            assertTrue(catalog.lookup(new FTNAddr("1:500/102")).isEmpty());
            assertEquals("Other",
                         catalog.lookup("other", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
        }
    }

    @Test
    void rollsBackAllFlushedBatchesWhenTheEndOfALargeSourceIsBroken() throws Exception {
        DirectorySource source = write("nodes", DirectoryParserTest.row("", 4000, "Original"));
        try (DirectoryCatalog catalog = catalog()) {
            catalog.importSource(source);
            StringBuilder body = new StringBuilder();
            for (int node = 1; node <= 1200; node++) {
                body.append(DirectoryParserTest.row("", node, "New"));
            }
            body.append(",broken\n");
            Files.writeString(source.getPath(), body);
            assertThrows(IOException.class, () -> catalog.importSource(source));
            assertEquals("Original",
                         catalog.lookup("nodes", new FTNAddr("1:500/4000"))
                                .orElseThrow()
                                .getSystem());
            assertTrue(catalog.lookup("nodes", new FTNAddr("1:500/1")).isEmpty());
        }
    }

    @Test
    void searchesLiteralNamesWithBoundedPagesAndRetainsMetadata() throws Exception {
        DirectorySource source = write("nodes",
                                       DirectoryParserTest.row("", 101, "A_100%")
                                                + DirectoryParserTest.row("", 102, "A_100%")
                                                + DirectoryParserTest.row("", 103, "A_100X"));
        DirectoryCatalog catalog = catalog();
        try (catalog) {
            catalog.importSource(source);
            List<DirectoryEntry> page = catalog.search("nodes", "_100%", 1, 1);
            assertEquals(1, page.size());
            assertEquals(new FTNAddr("1:500/102"), page.get(0).getAddress());
            assertEquals(3, catalog.search("nodes", "jane DOE", 0, 10).size());
            assertThrows(IllegalArgumentException.class, () -> catalog.search("nodes", "", 0, 501));
            assertThrows(IllegalArgumentException.class, () -> catalog.search("nodes", "", -1, 10));
        }
        assertThrows(IOException.class, () -> catalog.lookup(new FTNAddr("1:500/101")));
    }
}
