package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FileTimestampValidationTest {
    @Test
    void rejectsNegativeTimestampsInOffersRequestsAndAcknowledgements() {
        for (String suffix : new String[]{"", " 0", " -1"}) {
            assertThrows(IllegalArgumentException.class,
                         () -> FileInfo.parseFileInfo("TEST.PKT 1 -1" + suffix));
        }
    }

    @Test
    void acceptsEpochAndWideTimestampsWithoutBreakingNrOffset() throws Exception {
        for (long timestamp : new long[]{0, 2147483648L, 4294967295L}) {
            FileInfo info = FileInfo.parseFileInfo("TEST.PKT 1 " + timestamp + " -1");
            assertEquals(timestamp, info.getTimestamp());
            assertEquals(-1, info.getOffset());
        }
    }
}
