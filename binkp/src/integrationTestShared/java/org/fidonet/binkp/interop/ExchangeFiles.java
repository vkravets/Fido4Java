package org.fidonet.binkp.interop;

import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Random;

/** Deterministic payloads shared by both role scenarios; no committed binaries. */
public final class ExchangeFiles {
    static final String FROM_JFTN = "from-jftn.bin";
    static final String FROM_BINKD = "from-binkd.bin";
    final byte[] toBinkd = randomBytes(20003, 11);
    final byte[] toJftn = randomBytes(17009, 12);

    public FileData<InputStream> outgoingFromJftn() {
        return new FileData<>(new FileInfo(FROM_JFTN, toBinkd.length, 1700000000L),
                new ByteArrayInputStream(toBinkd));
    }

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }
}
