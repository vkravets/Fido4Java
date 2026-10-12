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

import org.fidonet.tools.CharsetTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtsPackMsg {

    private static final Logger logger = LoggerFactory.getLogger(FtsPackMsg.class.getName());

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
        buf.order(ByteOrder.LITTLE_ENDIAN);
        if (buf.remaining() < 34) throw new IllegalArgumentException("Truncated packed message header");
        type = buf.getShort();
        if (type != 2) {
            logger.debug("Seems like not a packet!");
            isValid = false;
            return;
        }
        origNode = Short.toUnsignedInt(buf.getShort());
        destNode = Short.toUnsignedInt(buf.getShort());
        origNet = Short.toUnsignedInt(buf.getShort());
        destNet = Short.toUnsignedInt(buf.getShort());
        attr = buf.getShort();
        cost = Short.toUnsignedInt(buf.getShort());
        final byte[] tmp = new byte[20];
        buf.get(tmp, 0, 20);
        dateTime = tmp;

        byte[] toBytes = readString(buf, 36);
        byte[] fromBytes = readString(buf, 36);
        byte[] subjectBytes = readString(buf, 72);
        body = readString(buf, Integer.MAX_VALUE);
        Charset charset = detectCharset(body);
        to = new String(toBytes, charset);
        from = new String(fromBytes, charset);
        subj = new String(subjectBytes, charset);
    }

    private static byte[] readString(ByteBuffer buf, int maxBytes) {
        int start = buf.position();
        while (buf.hasRemaining()) {
            if (buf.position() - start >= maxBytes) throw new IllegalArgumentException("Packed message field exceeds its maximum size");
            if (buf.get() == 0) {
                int end = buf.position();
                byte[] value = new byte[end - start - 1];
                buf.position(start); buf.get(value); buf.position(end);
                return value;
            }
        }
        throw new IllegalArgumentException("Unterminated packed message field");
    }

    public static Charset detectCharset(byte[] text) {
        Matcher matcher = Pattern.compile("(?:^|\\r)\\n?\\u0001(?:CHRS|CHARSET):\\s*([^\\s\\r]+)")
                .matcher(new String(text, StandardCharsets.ISO_8859_1));
        return CharsetTools.charsetDetect(matcher.find() ? matcher.group(1) : CharsetTools.DEFAULT_ENCODING);
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
