package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fidonet.binkp.common.codec.DataBulk;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class DataBulkSnapshotTest {
    @Test
    public void queuedFramesKeepTheirContentsWhenTheReadBufferIsReused() {
        byte[] buffer = {1, 2, 3, 4};
        BinkFrame first = new DataBulk(buffer, buffer.length).getRawData();
        Arrays.fill(buffer, (byte) 9);
        BinkFrame second = new DataBulk(buffer, 2).getRawData();
        Arrays.fill(buffer, (byte) 0);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, first.getData());
        assertArrayEquals(new byte[]{9, 9}, second.getData());
        assertEquals(4, first.getDataInfo());
        assertEquals(2, second.getDataInfo());
    }
}
