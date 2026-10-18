package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

class TicDocumentTest {
    @TempDir
    Path root;

    @Test
    void rejectsMissingNegativeNonnumericAndOverflowingPathTimes() {
        for (String path : new String[]{"1:500/101", "1:500/101 -1", "1:500/101 tomorrow",
                "1:500/101 9223372036854775808"}) {
            assertThrows(IOException.class, () -> document(path));
        }
    }

    @Test
    void acceptsEpochAndWideTimestampsAndPreservesOptionalDateComment() throws Exception {
        for (String time : new String[]{"0", "2147483648", "4294967295"}) {
            String path = "1:500/101 " + time + " Tue Nov 14 22:13:20 2023 UTC";
            TicDocument document = document(path);
            assertEquals(Set.of(new FTNAddr("1:500/101")), document.addresses("path"));
            assertEquals(path, document.value("path"));
        }
    }

    @Test
    void rejectsInvalidPathAddressesAndReportsMalformedNumbersAsIoFailures() {
        for (String address : new String[]{"1:500/-1", "1:500/70000", "1:500/node",
                "1:500/99999999999999999999"}) {
            assertThrows(IOException.class, () -> document(address + " 1700000000"));
        }
    }

    private TicDocument document(String path) throws IOException {
        Path ticket = root.resolve("TEST.TIC");
        Files.writeString(ticket,
                          "Area TEST\r\nFile TEST.TXT\r\nFrom 1:500/101\r\n"
                          + "Origin 1:500/101\r\nCrc 00000000\r\nPath "
                          + path
                          + "\r\nSeenby 1:500/101\r\n");
        return new TicDocument(ticket, StandardCharsets.US_ASCII);
    }
}
