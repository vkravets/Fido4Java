package org.fidonet.fts;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class FtsMsg {
    private final String From;
    private final String To;
    private final String Subject;
    private final String Date;
    private final int Readed;
    private final int destNode;
    private final int origNode;
    private final int cost;
    private final int origNet;
    private final int destNet;
    private final long wrTime;
    private final long arTime;
    private final int replyTo;
    private final int attr;
    private final int nextreplay;
    private final String Text;

    public FtsMsg(byte[] arr) {
        final ByteBuffer x = ByteBuffer.allocate(arr.length);
        x.put(arr);
        x.rewind();
        x.order(ByteOrder.LITTLE_ENDIAN);
        byte[] tmp = new byte[36];
        x.get(tmp, 0, 36);
        From = new String(tmp).trim();
        x.get(tmp, 0, 36);
        To = new String(tmp).trim();
        tmp = new byte[72];
        x.get(tmp, 0, 72);
        Subject = new String(tmp).trim();
        tmp = new byte[20];
        x.get(tmp, 0, 20);
        Date = new String(tmp).trim();
        Readed = x.getShort();
        destNode = x.getShort();
        origNode = x.getShort();
        cost = x.getShort();
        origNet = x.getShort();
        destNet = x.getShort();
        wrTime = x.getInt();
        arTime = x.getInt();
        replyTo = x.getShort();
        attr = x.getShort();
        nextreplay = x.getShort();
        tmp = new byte[x.remaining()];
        x.get(tmp, 0, x.remaining());
        Text = new String(tmp);
    }

}
