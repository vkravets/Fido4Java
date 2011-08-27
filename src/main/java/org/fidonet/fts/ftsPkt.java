package org.fidonet.fts;

import org.fidonet.types.FTNAddr;

import java.nio.ByteBuffer;
import java.util.LinkedList;

public class ftsPkt {
    private int origNode;
    private int destNode;
    private int year;
    private int month;
    private int day;
    private int hour;
    private int min;
    private int sec;
    private int boud;
    private int type;
    private int origNet;
    private int destNet;
    private byte prodCodeLow;
    private byte prodCodeMaj;
    private String pass;
    private int origZone0;
    private int destZone0;
    private int auxNet;
    private int capWordCopy;
    private byte prodCodeH;
    private byte prodCodeMin;
    private int capWord;
    private int origZone;
    private int destZone;
    private int origPoint;
    private int destPoint;
    private byte[] achData; //4!!!
    private ftsPackMsg[] ftsMsgs;

    private FTNAddr origaddr;

    public ftsPkt() {

    }

    public ftsPkt(ByteBuffer x) {
//        final ByteBuffer x = ByteBuffer.allocate(arr.length);
//        x.put(arr);
//        x.rewind();
//        x.order(ByteOrder.LITTLE_ENDIAN);
        origNode = x.getShort();
        destNode = x.getShort();
        year = x.getShort();
        month = x.getShort();
        day = x.getShort();
        hour = x.getShort();
        min = x.getShort();
        sec = x.getShort();
        boud = x.getShort();
        type = x.getShort();
        origNet = x.getShort();
        destNet = x.getShort();
        prodCodeLow = x.get();
        prodCodeMaj = x.get();
        byte[] tmp = new byte[8];
        x.get(tmp, 0, 8);
        pass = new String(tmp).trim();
        origZone0 = x.getShort();
        destZone0 = x.getShort();
        auxNet = x.getShort();
        capWordCopy = x.getShort();
        prodCodeH = x.get();
        prodCodeMin = x.get();
        capWord = x.getShort();
        origZone = x.getShort();
        destZone = x.getShort();
        origPoint = x.getShort();
        destPoint = x.getShort();
        tmp = new byte[4];
        x.get(tmp, 0, 4);
        achData = tmp;
        ftsMsgs = getMsgPack(x);
        origaddr = new FTNAddr(origZone, origNet, origNode, origPoint);
    }

    private static ftsPackMsg[] getMsgPack(ByteBuffer b) {
        //final Vector<ftsPackMsg> tmp = new Vector<ftsPackMsg>(5);
        LinkedList<ftsPackMsg> tmp = new LinkedList<ftsPackMsg>();

        while (b.position() < b.limit() - 3)    // PKT ends whit 00 00
        {
            final ftsPackMsg m = new ftsPackMsg(b);
            if (m.isValid()) {
                tmp.add(m);
            }
        }
        ftsPackMsg[] result = new ftsPackMsg[tmp.size()];

        result = tmp.toArray(result);
        return result;
    }


    public byte[] getAchData() {
        return achData;
    }

    public int getAuxNet() {
        return auxNet;
    }

    public int getBoud() {
        return boud;
    }

    public int getCapWord() {
        return capWord;
    }

    public int getCapWordCopy() {
        return capWordCopy;
    }

    public void setCapWordCopy(int _capWordCopy) {
        this.capWordCopy = _capWordCopy;
    }

    public int getDay() {
        return day;
    }

    public int getDestNet() {
        return destNet;
    }

    public int getDestNode() {
        return destNode;
    }

    public int getDestPoint() {
        return destPoint;
    }

    public int getDestZone0() {
        return destZone0;
    }

    public int getDestZone() {
        return destZone;
    }

    public void setDestZone(int _destZone) {
        this.destZone = _destZone;
    }

    public int getHour() {
        return hour;
    }

    public int getMin() {
        return min;
    }

    public int getMonth() {
        return month;
    }

    public ftsPackMsg[] getMsgs() {
        return ftsMsgs;
    }

    void setMsgs(ftsPackMsg[] _ftsMsgs) {
        ftsMsgs = _ftsMsgs;
    }

    public int getOrigNet() {
        return origNet;
    }

    void setOrigNet(int _origNet) {
        this.origNet = _origNet;
    }

    public int getOrigNode() {
        return origNode;
    }

    public int getOrigPoint() {
        return origPoint;
    }

    public int getOrigZone0() {
        return origZone0;
    }

    public int getOrigZone() {
        return origZone;
    }

    public String getPass() {
        return pass;
    }

    public byte getProdCodeH() {
        return prodCodeH;
    }

    public byte getProdCodeLow() {
        return prodCodeLow;
    }

    public byte getProdCodeMaj() {
        return prodCodeMaj;
    }

    public byte getProdCodeMin() {
        return prodCodeMin;
    }

    public int getSec() {
        return sec;
    }

    public int getType() {
        return type;
    }

    public int getYear() {
        return year;
    }

    public FTNAddr getOrigaddr() {
        return origaddr;
    }
}
