/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.fts;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

public class FtsPackMsg {

    private static final ILogger logger = LoggerFactory.getLogger(FtsPackMsg.class.getName());

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

    private boolean isValid = true;


    public FtsPackMsg() {

    }

    public FtsPackMsg(ByteBuffer buf) {
        type = buf.getShort();
        if (type != 2) {
            logger.debug("Seems like not a packet!");
            isValid = false;
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
        return isValid;
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
        return Subj.clone();
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
        return splittedtext.clone();
    }

    public byte[] getDateTime() {
        return DateTime.clone();
    }
}
