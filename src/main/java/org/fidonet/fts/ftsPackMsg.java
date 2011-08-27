package org.fidonet.fts;

import org.fidonet.misc.Logger;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

public class ftsPackMsg {
    private int type;
    private int origNode;
    private int destNode;
    private int origNet;
    private int destNet;
    private short attr;
    private int cost;
    private byte[] DateTime; // 20 bytes
    private byte[] From; // 36 bytes
    private byte[] To; // 36
    private byte[] Subj;
    private String Text = "";
    private String[] splittedtext;
    public byte[] body;

    private static boolean Valid = true;


    public ftsPackMsg() {

    }

    public ftsPackMsg(ByteBuffer buf) {
        type = buf.getShort();
        if (type != 2) {
            Logger.Log("Seems like not a packet!");
            Valid = false;
            return;
        }
        origNode = buf.getShort();
        destNode = buf.getShort();
        origNet = buf.getShort();
        destNet = buf.getShort();
        attr = buf.getShort();
        cost = buf.getShort();
        final byte[] tmp = new byte[20];
        buf.get(tmp, 0, 20);
        DateTime = tmp;

        ByteBuffer tmpbuffer = ByteBuffer.allocate(65535);
        byte end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }

        To = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(To);
        tmpbuffer.position(0);

        end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }
        From = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(From);
        tmpbuffer.position(0);

        end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }
        Subj = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(Subj);
        tmpbuffer.position(0);

        end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }

//        Text = new String(tmpx.array(), 0, tmpx.position() - 1);
        body = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(body);
        tmpbuffer.position(0);

        Text = new String(body);

        Pattern newstr = Pattern.compile("\r");
        splittedtext = newstr.split(Text);
    }

    public boolean isValid() {
        return Valid;
    }

    public String getAchData() {
        return new String(DateTime);
    }

    public short getAttr() {
        return attr;
    }

    public int getCost() {
        return cost;
    }

    public int getDestNet() {
        return destNet;
    }

    public int getDestNode() {
        return destNode;
    }

    public String getFrom() {
        return new String(From);
    }

    public int getOrigNet() {
        return origNet;
    }

    public int getOrigNode() {
        return origNode;
    }

    public byte[] getSubj() {
        return Subj;
    }

    public String getText() {
        return Text;
    }

    public String getTo() {
        return new String(To);
    }

    public int getType() {
        return type;
    }

    public String[] getSplittedtext() {
        return splittedtext;
    }

    public byte[] getDateTime() {
        return DateTime;
    }
}
