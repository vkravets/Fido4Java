package org.fidonet.binkp.common.io;

import org.fidonet.binkp.common.codec.DataBulk;
import org.junit.Test;
import static org.junit.Assert.*;

public class DataBulkSnapshotTest {
    @Test public void queuedFramesKeepTheirContentsWhenTheReadBufferIsReused() {
        byte[] buffer={1,2,3,4};
        BinkFrame first=new DataBulk(buffer,buffer.length).getRawData();
        java.util.Arrays.fill(buffer,(byte)9);
        BinkFrame second=new DataBulk(buffer,2).getRawData();
        java.util.Arrays.fill(buffer,(byte)0);
        assertArrayEquals(new byte[]{1,2,3,4},first.getData());
        assertArrayEquals(new byte[]{9,9},second.getData());
        assertEquals(4,first.getDataInfo());assertEquals(2,second.getDataInfo());
    }
}
