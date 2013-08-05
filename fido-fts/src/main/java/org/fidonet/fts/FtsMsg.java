/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

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
