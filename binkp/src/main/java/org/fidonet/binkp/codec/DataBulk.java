package org.fidonet.binkp.codec;

import org.fidonet.binkp.io.BinkFrame;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 7:16 PM
 */
public class DataBulk implements Data {

    private byte[] data;
    private int len;

    public DataBulk(byte[] data) {
        this.data = data;
        this.len = data.length;
    }

    public DataBulk(byte[] data, int len) {
        this.data = data;
        this.len = len;
    }

    @Override
    public BinkFrame getRawData() {
        int len = this.len & 0x7fff;
        byte[] buf;
        if (data.length != this.len) {
            ByteBuffer _ = ByteBuffer.allocate(this.len);
            _.put(data, 0, this.len);
            buf = _.array();
        } else {
            buf = data;
        }
        return new BinkFrame((short) len, buf);
    }

    @Override
    public boolean isCommand() {
        return false;
    }
}
