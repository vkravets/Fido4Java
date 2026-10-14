/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.fts;

import org.fidonet.types.FTNAddr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class FtsPkt {
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
    private byte[] achData; // 4!!!
    private FtsPackMsg[] ftsMsgs;

    private FTNAddr origaddr;

    public FtsPkt() {
    }

    public FtsPkt(ByteBuffer x) {
        x.order(ByteOrder.LITTLE_ENDIAN);
        if (x.remaining() < 60) {
            throw new IllegalArgumentException("Truncated packet header or missing terminator");
        }
        //        final ByteBuffer x = ByteBuffer.allocate(arr.length);
        //        x.put(arr);
        //        x.rewind();
        //        x.order(ByteOrder.LITTLE_ENDIAN);
        origNode = Short.toUnsignedInt(x.getShort());
        destNode = Short.toUnsignedInt(x.getShort());
        year = x.getShort();
        month = x.getShort();
        day = x.getShort();
        hour = x.getShort();
        min = x.getShort();
        sec = x.getShort();
        boud = x.getShort();
        type = x.getShort();
        origNet = Short.toUnsignedInt(x.getShort());
        destNet = Short.toUnsignedInt(x.getShort());
        prodCodeLow = x.get();
        prodCodeMaj = x.get();
        byte[] tmp = new byte[8];
        x.get(tmp, 0, 8);
        int passwordLength = 0;
        while (passwordLength < tmp.length && tmp[passwordLength] != 0) {
            passwordLength++;
        }
        pass = new String(tmp, 0, passwordLength, StandardCharsets.US_ASCII);
        origZone0 = Short.toUnsignedInt(x.getShort());
        destZone0 = Short.toUnsignedInt(x.getShort());
        auxNet = Short.toUnsignedInt(x.getShort());
        capWordCopy = x.getShort();
        prodCodeH = x.get();
        prodCodeMin = x.get();
        capWord = x.getShort();
        origZone = Short.toUnsignedInt(x.getShort());
        destZone = Short.toUnsignedInt(x.getShort());
        origPoint = x.getShort();
        destPoint = x.getShort();
        tmp = new byte[4];
        x.get(tmp, 0, 4);
        achData = tmp;
        if (type != 2) {
            throw new IllegalArgumentException("Unsupported packet type: " + type);
        }
        boolean extended = Short.toUnsignedInt((short) capWordCopy) == Short.toUnsignedInt(Short.reverseBytes((short) capWord))
                && (capWord & 1) != 0;
        if (extended) {
            origPoint = Short.toUnsignedInt((short) origPoint);
            destPoint = Short.toUnsignedInt((short) destPoint);
            if (origPoint != 0 && origNet == 0xffff) {
                origNet = auxNet;
            }
            if (origZone == 0) {
                origZone = origZone0;
            }
            if (destZone == 0) {
                destZone = destZone0;
            }
        } else {
            origZone = origZone0;
            destZone = destZone0;
            origPoint = 0;
            destPoint = 0;
        }
        ftsMsgs = getMsgPack(x);
        origaddr = new FTNAddr(origZone, origNet, origNode, origPoint);
    }

    private static FtsPackMsg[] getMsgPack(ByteBuffer b) {
        // final Vector<ftsPackMsg> tmp = new Vector<ftsPackMsg>(5);
        LinkedList<FtsPackMsg> tmp = new LinkedList<FtsPackMsg>();

        boolean terminated = false;
        while (b.remaining() >= 2) {
            if (b.getShort(b.position()) == 0) {
                b.getShort();
                terminated = true;
                break;
            }
            final FtsPackMsg m = new FtsPackMsg(b);
            if (!m.isValid()) {
                throw new IllegalArgumentException("Invalid packed message type");
            }
            tmp.add(m);
        }
        if (!terminated || b.hasRemaining()) {
            throw new IllegalArgumentException("Invalid packet terminator");
        }
        FtsPackMsg[] result = new FtsPackMsg[tmp.size()];

        result = tmp.toArray(result);
        return result;
    }

    public byte[] getAchData() {
        return achData.clone();
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

    public FtsPackMsg[] getMsgs() {
        return ftsMsgs.clone();
    }

    void setMsgs(FtsPackMsg[] _ftsMsgs) {
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
