package org.fidonet.mina.io;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 9:28 PM
 */
public class BinkData {

    private boolean isCommand;
    private byte command;
    private byte[] data;

    public BinkData(boolean isCommand, byte command, byte[] data) {
        this.isCommand = isCommand;
        this.command = command;
        this.data = data;
    }

    public boolean isCommand() {
        return isCommand;
    }

    public byte getCommand() {
        return command;
    }

    public byte[] getData() {
        return data;
    }

    public static BinkFrame toBinkFrame(BinkData data) {
        int len = data.getData().length;
        ByteBuffer buf;
        if (data.isCommand()) {
            buf = ByteBuffer.allocate(len+1);
            buf.put(data.getCommand());
            len = +1;
            len |= 0x8000;
        } else {
            buf = ByteBuffer.allocate(len);
            buf.put(data.getData());
            len &= 0x7fff;
        }
        return new BinkFrame((short) len, buf.array());
    }
}
