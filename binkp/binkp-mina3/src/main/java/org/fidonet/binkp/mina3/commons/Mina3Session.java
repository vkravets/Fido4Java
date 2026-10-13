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

package org.fidonet.binkp.mina3.commons;

import org.apache.mina.api.IoSession;
import org.apache.mina.session.AttributeKey;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.io.FilesSender;
import org.fidonet.binkp.common.protocol.Session;

/**
 * Created by vkravets on 14.10.15.
 */
public class Mina3Session implements Session {

    private IoSession session;

    public Mina3Session(IoSession session) {
        this.session = session;
    }

    @Override
    public void write(Object message) {
        // MINA3's filter-chain cursor belongs to the session, not to each write.
        // File sender and protocol replies can otherwise run the chain concurrently.
        synchronized (session) {
            session.setAttribute(SessionKeys.LAST_WRITE_KEY, session.writeWithFuture(message));
        }
    }

    @Override
    public void close(boolean close) {
        synchronized (session) {
            org.apache.mina.api.IoFuture<Void> lastWrite = session.getAttribute(SessionKeys.LAST_WRITE_KEY);
            if (close || lastWrite == null) {
                session.close(true);
            } else {
                // MINA3-M2 close(false) can remain CLOSING when the queue is already empty.
                // Wait for the last queued write, then explicitly close the transport.
                lastWrite.register(new org.apache.mina.api.IoFutureListener<Void>() {
                    @Override public void completed(Void value) { session.close(true); }
                    @Override public void exception(Throwable failure) { session.close(true); }
                });
            }
        }
    }

    @Override
    public FilesSender getFileSender() {
        return session.getAttribute(SessionKeys.FILESENDER_KEY);
    }

    @Override
    public void setFileSender(FilesSender filesSender) {
        session.setAttribute(SessionKeys.FILESENDER_KEY, filesSender);
    }

    @Override
    public TrafficCrypter getTrafficCrypter() {
        return session.getAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY);
    }

    @Override
    public void setTrafficCrypter(TrafficCrypter trafficCrypter) {
        session.setAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY, trafficCrypter);
    }
}
