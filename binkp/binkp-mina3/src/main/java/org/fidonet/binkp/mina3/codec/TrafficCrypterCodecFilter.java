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

package org.fidonet.binkp.mina3.codec;

import org.apache.mina.api.AbstractIoFilter;
import org.apache.mina.api.IoSession;
import org.apache.mina.filterchain.ReadFilterChainController;
import org.apache.mina.filterchain.WriteFilterChainController;
import org.apache.mina.session.WriteRequest;

import java.nio.ByteBuffer;

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
public class TrafficCrypterCodecFilter extends AbstractIoFilter {

    @Override
    public void messageReceived(IoSession session, Object message, ReadFilterChainController controller) {
        ByteBuffer in = (ByteBuffer) message;
        TrafficCrypter trafficCrypter = session.getAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY);
        byte[] array = new byte[in.array().length];
        ByteBuffer buf = ByteBuffer.allocate(array.length);
        in.get(array);
        trafficCrypter.decrypt(array, array.length);
        buf.put(array);
        buf.flip();
        super.messageReceived(session, buf, controller);
    }

    @Override
    public void messageWriting(IoSession session, WriteRequest message, WriteFilterChainController controller) {
        ByteBuffer in = (ByteBuffer) message.getMessage();
        if (in.array().length > 0) {
            TrafficCrypter trafficCrypter = session.getAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY);
            byte[] array = new byte[in.array().length];
            ByteBuffer buf = ByteBuffer.allocate(array.length);
            in.get(array);
            trafficCrypter.encrypt(array, array.length);
            buf.put(array);
            buf.flip();
            message.setMessage(buf);
            super.messageWriting(session, message, controller);
        }
    }

}
