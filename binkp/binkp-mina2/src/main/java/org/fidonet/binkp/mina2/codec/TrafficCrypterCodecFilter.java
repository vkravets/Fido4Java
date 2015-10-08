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

package org.fidonet.binkp.mina2.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.mina2.commons.SessionKeys;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/14/14
 * Time: 9:56 AM
 */

/**
 * This class control decrypt process for received data,
 * crypt process during sending data controls by last encoder
 */
public class TrafficCrypterCodecFilter extends IoFilterAdapter {

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        IoBuffer in = (IoBuffer) message;
        TrafficCrypter trafficCrypter = (TrafficCrypter) session.getAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY);
        byte[] array = new byte[in.limit()];
        IoBuffer buf = IoBuffer.allocate(array.length);
        in.get(array);
        trafficCrypter.decrypt(array, array.length);
        buf.put(array);
        buf.flip();
        super.messageReceived(nextFilter, session, buf);
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        IoBuffer in = (IoBuffer) writeRequest.getMessage();
        if (in.limit() > 0) {
            TrafficCrypter trafficCrypter = (TrafficCrypter) session.getAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY);
            byte[] array = new byte[in.limit()];
            in.get(array);
            trafficCrypter.encrypt(array, array.length);
            in.clear();
            in.put(array);
            in.flip();
        }
        super.filterWrite(nextFilter, session, writeRequest);
    }
}
