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

package org.fidonet.binkp.io;

import org.fidonet.binkp.codec.DataInfo;
import org.fidonet.binkp.codec.DataReader;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:22 PM
 */
public class BinkFrame {

    private static final short MAX_DATA_SIZE = 32767;

    private short dataInfo;
    private byte[] data;

    public BinkFrame(short dataInfo, byte[] data) {
        this.dataInfo = dataInfo;
        this.data = data;
    }

    public short getDataInfo() {
        return dataInfo;
    }

    public byte[] getData() {
        return data;
    }

    public static BinkData toBinkData(BinkFrame frame) {
        DataInfo info = DataReader.parseDataInfo(frame.getDataInfo());
        byte cmd = -1;
        byte[] dataBuf = null;
        if (info.isCommand()) {
            DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(frame.getData()));
            try {
                cmd = dataStream.readByte();
                dataBuf = new byte[info.getSize()-1];
                dataStream.read(dataBuf);
            } catch (IOException e) {
                // todo logger
            }
        } else {
            dataBuf = frame.getData();
        }
        return new BinkData(info.isCommand(), cmd, dataBuf);

    }

}
