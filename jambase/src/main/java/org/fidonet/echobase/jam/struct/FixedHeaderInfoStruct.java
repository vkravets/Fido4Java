package org.fidonet.echobase.jam.struct;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 18.11.2010
 * Time: 12:48:21
 */
public class FixedHeaderInfoStruct {
    private final byte[] signature = {'J', 'A', 'M', 0};
    private int datecreated;
    private int modcounter;
    private int activemsg;
    private int passwordcrc;
    private int basemsgnum;
    private final byte[] reserved = new byte[1000];

    public byte[] toByteArray() {
        ByteBuffer res = ByteBuffer.allocate(1024);
        res.order(ByteOrder.LITTLE_ENDIAN);
        res.put(signature);
        res.putInt(datecreated);
        res.putInt(modcounter);
        res.putInt(activemsg);
        res.putInt(passwordcrc);
        res.putInt(basemsgnum);
        res.put(reserved);
        return res.array();
    }

    public void fromByteArray(ByteBuffer income) {
        income.position(0);
        income.order(ByteOrder.LITTLE_ENDIAN);
        income.get(signature);
        datecreated = income.getInt();
        modcounter = income.getInt();
        activemsg = income.getInt();
        passwordcrc = income.getInt();
        basemsgnum = income.getInt();
        income.get(reserved);
    }

    public int getDatecreated() {
        return datecreated;
    }

    public void setDatecreated(int datecreated) {
        this.datecreated = datecreated;
    }

    public int getModcounter() {
        return modcounter;
    }

    public void setModcounter(int modcounter) {
        this.modcounter = modcounter;
    }

    public int getActivemsg() {
        return activemsg;
    }

    public void setActivemsg(int activemsg) {
        this.activemsg = activemsg;
    }

    public int getPasswordcrc() {
        return passwordcrc;
    }

    public void setPasswordcrc(int passwordcrc) {
        this.passwordcrc = passwordcrc;
    }

    public int getBasemsgnum() {
        return basemsgnum;
    }

    public void setBasemsgnum(int basemsgnum) {
        this.basemsgnum = basemsgnum;
    }
}
