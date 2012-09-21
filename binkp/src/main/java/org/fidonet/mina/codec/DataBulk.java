package org.fidonet.mina.codec;

import org.fidonet.mina.io.BinkFrame;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 7:16 PM
 */
public class DataBulk implements Data {

    private byte[] data;

    public DataBulk(byte[] data) {
        this.data = data;
    }

    @Override
    public BinkFrame getRawData() {
        int len = data.length & 0x7fff;
        return new BinkFrame((short) len, data);
    }

    @Override
    public boolean isCommand() {
        return false;
    }
}
