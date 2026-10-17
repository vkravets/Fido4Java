package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DirectoryEditionTest {
    @TempDir
    Path root;

    private String header(String date, int day) {
        return ";A Region Nodelist for Friday, " + date + " -- Day number " + day + " : 00000\r\n";
    }

    private Path plain(String text) throws IOException {
        Path file = root.resolve("list.txt");
        Files.writeString(file, text);
        return file;
    }

    @Test
    void readsPublicationYearAndDayFromPlainAndZipWithoutDependingOnFilename() throws Exception {
        String text = header("October 16, 2026", 289);
        assertEquals(Optional.of(LocalDate.of(2026, 10, 16)), DirectoryEdition.read(plain(text)));
        Path zip = root.resolve("NODELIST.Z89");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("NODELIST.289"));
            output.write(text.getBytes(StandardCharsets.US_ASCII));
            output.closeEntry();
        }
        assertEquals(Optional.of(LocalDate.of(2026, 10, 16)), DirectoryEdition.read(zip));
    }

    @Test
    void rejectsImpossibleOrInconsistentDatesAndDoesNotGuessHeaderlessEditions() throws Exception {
        assertThrows(IOException.class,
                     () -> DirectoryEdition.read(plain(header("February 29, 2026", 60))));
        assertThrows(IOException.class,
                     () -> DirectoryEdition.read(plain(header("October 16, 2026", 288))));
        assertEquals(Optional.empty(), DirectoryEdition.read(plain(";A Day 289 : 00000\n")));
        assertEquals(Optional.empty(),
                     DirectoryEdition.read(plain(",101,Node,City,Sysop,-Unpublished-,300\n")));
    }
}
