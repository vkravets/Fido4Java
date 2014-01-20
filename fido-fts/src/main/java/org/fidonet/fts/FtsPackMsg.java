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
import org.fidonet.tools.CharsetTools;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class FtsPackMsg {

    private static final ILogger logger = LoggerFactory.getLogger(FtsPackMsg.class.getName());

    private int type;
    private int origNode;
    private int destNode;
    private int origNet;
    private int destNet;
    private short attr;
    private int cost;
    private byte[] dateTime; // 20 bytes
    private String from; // 36 bytes
    private String to; // 36
    private String subj;
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
        dateTime = tmp;

        ByteBuffer tmpbuffer = ByteBuffer.allocate(65535);
        byte end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }

        byte[] to_array = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(to_array);
        tmpbuffer.position(0);
        to = new String(to_array, Charset.forName(CharsetTools.DEFAULT_ENCODING));

        end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }
        byte[] from_array = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(from_array);
        tmpbuffer.position(0);
        from = new String(from_array, Charset.forName(CharsetTools.DEFAULT_ENCODING));

        end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }
        byte[] subj_array = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(subj_array);
        tmpbuffer.position(0);
        subj = new String(subj_array, Charset.forName(CharsetTools.DEFAULT_ENCODING));

        end = 1;
        while (end != 0) {
            end = buf.get();
            tmpbuffer.put(end);
        }

        byte[] body_array = new byte[tmpbuffer.position() - 1];
        tmpbuffer.position(0);
        tmpbuffer.get(body_array);
        tmpbuffer.position(0);
        body = new byte[body_array.length];
        System.arraycopy(body_array, 0, body, 0, body_array.length);
    }

    public boolean isValid() {
        return isValid;
    }

    public String getAchData() {
        return new String(dateTime);
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
        return from;
    }

    public int getOrigNet() {
        return origNet;
    }

    public int getOrigNode() {
        return origNode;
    }

    public String getSubj() {
        return subj;
    }

    public String getTo() {
        return to;
    }

    public byte[] getBody() {
        return body;
    }

    public int getType() {
        return type;
    }

    public byte[] getDateTime() {
        return dateTime.clone();
    }
}
