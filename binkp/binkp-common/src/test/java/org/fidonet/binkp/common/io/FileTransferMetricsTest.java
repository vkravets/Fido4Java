package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FileTransferMetricsTest {
    @Test
    void resumeCountsOnlyNewBytesAndDoesNotRestartForRepeatedHeaders() {
        FileData<Object> file = new FileData<>(new FileInfo("resume.pkt", 100, 1700000000));
        assertTrue(file.startTransfer(40));
        file.getInfo().setCurSize(100);
        assertFalse(file.startTransfer(0));
        assertEquals(60, file.getTransferredBytes());
        assertTrue(file.getTransferMillis() >= 1);
    }

    @Test
    void emptyFileHasZeroTransferredBytesWithoutDivisionByZero() {
        FileData<Object> file = new FileData<>(new FileInfo("empty.pkt", 0, 1700000000));
        assertEquals(0, file.getTransferMillis());
        file.startTransfer(0);
        assertEquals(0, file.getTransferredBytes());
        assertTrue(file.getTransferMillis() >= 1);
    }
}
