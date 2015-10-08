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

package org.fidonet.binkp.mina3.codec;

import org.apache.mina.codec.IoBuffer;
import org.apache.mina.codec.ProtocolDecoder;
import org.fidonet.binkp.common.codec.DataInfo;
import org.fidonet.binkp.common.codec.DataReader;
import org.fidonet.binkp.common.io.BinkFrame;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:20 PM
 */
public class BinkDataDecoder implements ProtocolDecoder<ByteBuffer, BinkFrame, BinkDataDecoder.Context> {

    public class Context {

        // Cumulative buffer which include all buffers, which were passed to decoder
        private IoBuffer buf;

        // Flag which indicating if message was decode
        // it's using to detect if passed buffer should be appended to cumulative buffer.
        // Mina performs decode process on the same buffer till decode will return null.
        // This is doing since in one message can be more then 1 message.
        private boolean messageDecoded;

        public Context() {
            this.buf = IoBuffer.newInstance();
            this.messageDecoded = false;
        }

        public IoBuffer getBuf() {
            return buf;
        }

        public void setMessageDecoded(boolean isMessageDecode) {
            messageDecoded = isMessageDecode;
        }

        public boolean isMessageDecoded() {
            return messageDecoded;
        }
    }

    @Override
    public Context createDecoderState() {
        return new Context();
    }

    @Override
    public BinkFrame decode(ByteBuffer in, Context out) {
        int start = out.getBuf().position();
        if (!out.isMessageDecoded()) {
            out.getBuf().add(in);
        }
        if (out.getBuf().remaining() < 2) {
            out.setMessageDecoded(false);
            return null;
        }

        short dataInfoRaw = out.getBuf().getShort();
        DataInfo dataInfo = DataReader.parseDataInfo(dataInfoRaw);

        if (dataInfo == null || out.getBuf().remaining() < dataInfo.getSize()) {
            out.setMessageDecoded(false);
            out.getBuf().position(start);
            return null;
        }
        byte[] dataBuf = new byte[dataInfo.getSize()];
        out.getBuf().get(dataBuf);
        out.setMessageDecoded(true);
        return new BinkFrame(dataInfoRaw, dataBuf);
    }

    @Override
    public void finishDecode(Context context) {
        context.buf.clear();
    }

}
