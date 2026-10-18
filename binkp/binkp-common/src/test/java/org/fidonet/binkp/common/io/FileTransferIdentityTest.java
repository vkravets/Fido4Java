package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class FileTransferIdentityTest {
    @Test
    void progressChangesDoNotLosePendingTransfersOrChangeWireIdentity() {
        FileTransferInfo transfer = new FileTransferInfo("LIST.ZIP", 100, 1700000000);
        FileMetadata identity = transfer.getMetadata();
        Map<FileTransferInfo, String> pending = new HashMap<>();
        pending.put(transfer, "pending");
        transfer.setOffset(25);
        transfer.setCurSize(100);
        transfer.setFinished(true);
        transfer.setSkip(true);
        assertEquals("pending", pending.get(new FileTransferInfo("LIST.ZIP", 100, 1700000000)));
        assertSame(identity, transfer.getMetadata());
        assertEquals("LIST.ZIP 100 1700000000", transfer.arguments());
    }
}
