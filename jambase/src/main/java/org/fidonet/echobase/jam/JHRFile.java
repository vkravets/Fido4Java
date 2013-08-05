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

package org.fidonet.echobase.jam;

import org.fidonet.echobase.jam.struct.FixedHeaderInfoStruct;
import org.fidonet.echobase.jam.struct.MessageHeader;
import org.fidonet.echobase.jam.struct.SubField;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

class JHRFile {

    private static final ILogger logger = LoggerFactory.getLogger(JHRFile.class.getName());

    private RandomAccessFile jhr;

    public JHRFile(File tmp) throws FileNotFoundException {
        jhr = new RandomAccessFile(tmp, "rw");
    }

    public MessageHeader getMsgHeaderByShift(int shift) throws IOException {
        jhr.seek(shift);
        if (shift < 1024) return null;
        MessageHeader result = new MessageHeader();

        ByteBuffer tmpbuf = ByteBuffer.allocate(MessageHeader.MessageHeaderSize);
        int readed = jhr.read(tmpbuf.array());
        if(readed != MessageHeader.MessageHeaderSize)
        {
            logger.error("Oops! getMsgHeaderByShift() reads MessageHeaderSize with error.");
        }
        tmpbuf.order(ByteOrder.LITTLE_ENDIAN);
        tmpbuf.position(0);

        tmpbuf.get(result.signature);
        result.revision = tmpbuf.getShort();
        result.reservedword = tmpbuf.getShort();
        result.SubfieldLen = tmpbuf.getInt();
        result.TimesRead = tmpbuf.getInt();
        result.MSGIDcrc = tmpbuf.getInt();
        result.REPLYcrc = tmpbuf.getInt();
        result.ReplyTo = tmpbuf.getInt();
        result.Reply1st = tmpbuf.getInt();
        result.Replynext = tmpbuf.getInt();
        result.DateWritten = tmpbuf.getInt();
        result.DateReceived = tmpbuf.getInt();
        result.DateProcessed = tmpbuf.getInt();
        result.MessageNumber = tmpbuf.getInt();
        result.Attribute = tmpbuf.getInt();
        result.Attribute2 = tmpbuf.getInt();
        result.Offset = tmpbuf.getInt();
        result.TxtLen = tmpbuf.getInt();
        result.PasswordCRC = tmpbuf.getInt();
        result.Cost = tmpbuf.getInt();
        if (result.SubfieldLen != 0) {
            tmpbuf = ByteBuffer.allocate(result.SubfieldLen);
            jhr.read(tmpbuf.array());
            tmpbuf.position(0);
            tmpbuf.order(ByteOrder.LITTLE_ENDIAN);
            result.SubFieldList = new LinkedList<SubField>();
            while (tmpbuf.position() < tmpbuf.capacity()) {
                SubField sf = new SubField();
                sf.loID = tmpbuf.getShort();
                sf.hiID = tmpbuf.getShort();
                sf.datalen = tmpbuf.getInt();
                sf.buffer = new byte[sf.datalen];
                tmpbuf.get(sf.buffer);
                result.SubFieldList.add(sf);
            }
        }
        return result;
    }

    public int writeHeader(MessageHeader msghdr) throws IOException {
        ByteBuffer tmpbuf = ByteBuffer.allocate(MessageHeader.MessageHeaderSize + msghdr.SubfieldLen);
        tmpbuf.position(0);
        tmpbuf.order(ByteOrder.LITTLE_ENDIAN);
        tmpbuf.put(msghdr.signature);
        tmpbuf.putShort(msghdr.revision);
        tmpbuf.putShort(msghdr.reservedword);
        tmpbuf.putInt(msghdr.SubfieldLen);
        tmpbuf.putInt(msghdr.TimesRead);
        tmpbuf.putInt(msghdr.MSGIDcrc);
        tmpbuf.putInt(msghdr.REPLYcrc); //TODO!!!
        tmpbuf.putInt(msghdr.ReplyTo);
        tmpbuf.putInt(msghdr.Reply1st);
        tmpbuf.putInt(msghdr.Replynext);
        tmpbuf.putInt(msghdr.DateWritten);
        tmpbuf.putInt(msghdr.DateReceived);
        tmpbuf.putInt(msghdr.DateProcessed);
        tmpbuf.putInt(msghdr.MessageNumber);
        tmpbuf.putInt(msghdr.Attribute); // why 1 by htp?
        tmpbuf.putInt(msghdr.Attribute2);
        tmpbuf.putInt(msghdr.Offset);
        tmpbuf.putInt(msghdr.TxtLen);
        tmpbuf.putInt(msghdr.PasswordCRC);
        tmpbuf.putInt(msghdr.Cost);
        if (msghdr.SubfieldLen != 0) {
            for (int i = 0; i < msghdr.SubFieldList.size(); i++) {
                SubField sub = msghdr.SubFieldList.get(i);
                tmpbuf.putShort(sub.loID);
                tmpbuf.putShort(sub.hiID);
                tmpbuf.putInt(sub.buffer.length);
                tmpbuf.put(sub.buffer);
            }
        }
        int result = -1;
        jhr.seek(jhr.length());
        result = (int) jhr.length();
        jhr.write(tmpbuf.array());
        return result;
    }

    public FixedHeaderInfoStruct getFixedHeader() throws IOException {
        byte[] fh = new byte[1024];
        ByteBuffer res = ByteBuffer.allocate(1024);
        int readed = 0;
        res.order(ByteOrder.LITTLE_ENDIAN);
        jhr.seek(0);
        readed = jhr.read(fh);
        if(readed != 1024)
        {
            logger.error("Ooops! getFixedHeader() reads not 1024!");
        }
        res.put(fh);
        FixedHeaderInfoStruct result = new FixedHeaderInfoStruct();
        result.fromByteArray(res);
        return result;
    }

    public void setFixedHeader(FixedHeaderInfoStruct fh) throws IOException {
        fh.setModcounter(fh.getModcounter() + 1);
        jhr.seek(0);
        jhr.write(fh.toByteArray());
    }

    void close() throws IOException {
        jhr.close();
    }

}
