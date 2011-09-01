package org.fidonet.misc;

import java.util.zip.CRC32;

public class MyCRC {

    private static final CRC32 crc32 = new CRC32();

    static public int CRC(byte[] bb) {
        crc32.reset();
        crc32.update(bb);
        return (int) crc32.getValue() ^ 0xFFFFFFFF;
    }

}
