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
import java.time.Instant;
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

    @Test
    void usesFirstRecordedHopRatherThanLaterRelayForIndexDate() throws Exception {
        TicDocument tic = document("1:500/101 757382400\r\nPath 1:500/102 1700000000");
        assertEquals(Instant.parse("1994-01-01T00:00:00Z"), tic.indexDate());
    }

    @Test
    void unrepresentableTimestampDoesNotInvalidateTheDelivery() throws Exception {
        assertEquals(null, document("1:500/101 9223372036854775807").indexDate());
    }

    @Test
    void readsHtickDateNamedRecipientAndNumericSizeWithoutOptionalRoutingLines() throws Exception {
        document("1:500/101 1700000000");
        Path file = root.resolve("TEST.TIC");
        String text = Files.readString(file)
                           .replace("Path 1:500/101 1700000000\r\n", "")
                           .replace("Seenby 1:500/101\r\n", "")
                      + "Date 757382400\r\nSize 000007\r\nTo Test Sysop, 1:500/100\r\n";
        Files.writeString(file, text);
        TicDocument tic = new TicDocument(file, StandardCharsets.US_ASCII);
        assertEquals(Instant.parse("1994-01-01T00:00:00Z"), tic.indexDate());
        assertEquals(new FTNAddr("1:500/100"), tic.recipient());
        assertEquals(true, tic.matchesSize(7));
        assertEquals(false, tic.matchesSize(8));
        assertEquals(Set.of(), tic.addresses("path"));
        assertEquals(Set.of(), tic.addresses("seenby"));
    }

    @Test
    void rejectsMalformedAndAmbiguousOptionalDatesAndSizes() throws Exception {
        for (String field : new String[]{"Date -1", "Date tomorrow", "Size -1", "Size 7x",
                "Date 9223372036854775808", "Date 1\r\nDate 2"}) {
            document("1:500/101 1700000000");
            Path file = root.resolve("TEST.TIC");
            Files.writeString(file, Files.readString(file) + field + "\r\n");
            assertThrows(IOException.class, () -> new TicDocument(file, StandardCharsets.US_ASCII));
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
