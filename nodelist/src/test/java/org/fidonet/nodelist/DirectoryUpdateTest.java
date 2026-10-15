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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DirectoryUpdateTest {
    @TempDir
    Path root;

    private String checked(String body, int day) {
        int crc = 0;
        for (byte value : body.getBytes(StandardCharsets.US_ASCII)) {
            crc = DirectoryParser.crc16(crc, value);
        }
        return ";A Day " + day + " : " + String.format("%05d", crc) + "\r\n" + body;
    }

    private String row(int node, String name) {
        return "," + node + "," + name + ",City,Sysop,-Unpublished-,300\r\n";
    }

    private DirectorySource source() {
        return new DirectorySource("nodes",
                                   DirectorySource.Format.NODELIST,
                                   root.resolve("base.lst"),
                                   1,
                                   500,
                                   StandardCharsets.US_ASCII,
                                   true);
    }

    private Path file(String name, String text) throws Exception {
        Path path = root.resolve(name);
        Files.writeString(path, text);
        return path;
    }

    private String diff(String old, String next) {
        return old.substring(0, old.indexOf('\r')) + "\r\nD2\r\nA2\r\n" + next;
    }

    @Test
    void importsFullEditionAndAppliesAllThreeDiffOperationsAndReplaysSafely() throws Exception {
        String shared = row(102, "Shared");
        String old = checked(row(101, "Old") + shared, 288);
        String next = checked(row(101, "New") + shared, 295);
        Path full = file("nodelist.288", old + '\u001a');
        Path patch = file("nodediff.295",
                          old.substring(0, old.indexOf('\r'))
                          + "\r\nD2\r\nA2\r\n"
                          + next.substring(0, next.indexOf(shared))
                          + "C1\r\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("catalog"))) {
            assertEquals(2, DirectoryUpdate.install(catalog, source(), full, false).getRecords());
            DirectoryUpdate.install(catalog, source(), patch, true);
            assertEquals(next + '\u001a', Files.readString(source().getPath()));
            assertEquals("New",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
            assertFalse(DirectoryUpdate.install(catalog, source(), patch, true).isChanged());
        }
    }

    @Test
    void rejectsBadCrcWrongBaseMalformedCommandsAndDuplicatesWithoutReplacingBaseOrCatalog()
            throws Exception {
        String old = checked(row(101, "Old"), 288);
        Path full = file("full", old);
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("catalog"))) {
            DirectoryUpdate.install(catalog, source(), full, false);
            for (String patch : List.of("wrong\r\nC2\r\n",
                                        old.substring(0, old.indexOf('\r')) + "\r\nC0\r\n",
                                        old.substring(0, old.indexOf('\r')) + "\r\nC32768\r\n",
                                        old.substring(0, old.indexOf('\r')) + "\r\nC3\r\n",
                                        diff(old,
                                             checked(row(101, "New"), 295).replace("New", "Bad")),
                                        old.substring(0, old.indexOf('\r')) + "\r\nC1\r\n")) {
                Path input = file("patch", patch);
                assertThrows(IOException.class,
                             () -> DirectoryUpdate.install(catalog, source(), input, true));
                assertEquals(old, Files.readString(source().getPath()));
            }
            Path duplicate = file("duplicate", checked(row(101, "One") + row(101, "Two"), 295));
            assertThrows(IOException.class,
                         () -> DirectoryUpdate.install(catalog, source(), duplicate, false));
            assertEquals(old, Files.readString(source().getPath()));
            assertEquals("Old",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
        }
    }

    @Test
    void acceptsSingleMemberZipDiffAndPointlistEditions() throws Exception {
        DirectorySource points = new DirectorySource("points",
                                                     DirectorySource.Format.BOSS,
                                                     root.resolve("points.lst"),
                                                     0,
                                                     0,
                                                     StandardCharsets.US_ASCII,
                                                     true);
        String old = checked("Boss,1:500/101\r\n" + row(1, "Old"), 288);
        String next = checked("Boss,1:500/101\r\n" + row(1, "New"), 295);
        Path full = file("points.288", old);
        Path archive = root.resolve("pointdiff.z95");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("POINTDIFF.295"));
            zip.write((old.substring(0, old.indexOf('\r')) + "\r\nD3\r\nA3\r\n"
                    + next).getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("catalog"))) {
            DirectoryUpdate.install(catalog, points, full, false);
            DirectoryUpdate.install(catalog, points, archive, true);
            assertEquals("New",
                         catalog.lookup("points", new FTNAddr("1:500/101.1"))
                                .orElseThrow()
                                .getSystem());
        }
    }

    @Test
    void recoversPublishedEditionWhenCatalogWasNotUpdatedAndRejectsMissingBase() throws Exception {
        String old = checked(row(101, "Old"), 288);
        String next = checked(row(101, "New"), 295);
        Path full = file("full", old);
        Path patch = file("patch", diff(old, next));
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("catalog"))) {
            assertThrows(IOException.class,
                         () -> DirectoryUpdate.install(catalog, source(), patch, true));
            DirectoryUpdate.install(catalog, source(), full, false);
            DirectoryUpdate.install(catalog, source(), patch, true);
            // Model a crash after file publication but before SQL import by removing the source.
            catalog.removeSource("nodes");
            assertTrue(catalog.lookup("nodes", new FTNAddr("1:500/101")).isEmpty());
            DirectoryUpdate.install(catalog, source(), patch, true);
            assertEquals("New",
                         catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                .orElseThrow()
                                .getSystem());
        }
    }
}
