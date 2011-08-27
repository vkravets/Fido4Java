package org.fidonet.binkp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 16.02.11
 * Time: 12:37
 */
class Frame {

    static final int TYPE_COMMAND = 1;
    static final int TYPE_DATA = 0;

    private static final byte M_NUL = 0;
    static final byte M_ADR = 1;
    static final byte M_PWD = 2;
    static final byte M_FILE = 3;
    static final byte M_OK = 4;
    static final byte M_EOB = 5;
    static final byte M_GOT = 6;
    private static final byte M_ERR = 7;
    static byte M_BSY = 8;
    static byte M_GET = 9;
    static byte M_SKIP = 10;

    private int type;
    //    short length;
    private byte[] data;

    Frame() {

    }

    public void setType(int type) {
        this.type = type;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] toByteArray() {
        short head = (short) data.length;
        if (type == TYPE_COMMAND) {
            head |= (1 << 16 - 1);
        }
        ByteBuffer buf = ByteBuffer.allocate(2 + data.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort(Short.reverseBytes(head));
        buf.put(data);
        return buf.array();
    }

    public Block parse() {
        if (type == TYPE_COMMAND) {
            Block bl = new Block();
            if (data[0] == M_NUL) {
                String s = new String(data, 1, data.length - 1);
                bl.type = 0;
                bl.value = s.getBytes();
                return bl;
            }
            if (data[0] == M_ADR) {
                bl.type = M_ADR;
                String s = new String(data, 2, data.length - 2);
                bl.value = s.getBytes();
                return bl;
            }
            if (data[0] == M_PWD) {
                bl.type = M_PWD;
                String s = new String(data, 2, data.length - 2);
                bl.value = s.getBytes();
                return bl;
            }
            if (data[0] == M_FILE) {
                String s = new String(data, 1, data.length - 1);
                bl.type = M_FILE;
                bl.value = s.getBytes();
                return bl;
            }
            if (data[0] == M_OK) {
                System.out.println("M_OK!");
                bl.value = null;
                bl.type = M_OK;
                return bl;
            }

            if (data[0] == M_ERR) {
                System.out.println("ERR: " + new String(data));
            }

            if (data[0] == M_EOB) {
                System.out.println("M_EOB!");
                bl.value = null;
                bl.type = M_EOB;
                return bl;
            }

        } else {
            Block b = new Block();
            b.type = 666;
            b.value = data.clone();
            return b;
        }
        return null;
    }

}
