/*
 * Copyright (c) 2012-2017, Vladimir Kravets
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met: Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the Fido4Java nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.fidonet.binkp.netty.plugin.handler;

import io.netty.channel.ChannelHandlerContext;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;
import org.fidonet.events.EventBus;
import org.fidonet.types.FTNAddr;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/11/17
 * Time: 23:00
 */
public class BinkServerSessionHandler extends BinkSessionHandler {

    private final AtomicInteger userConnected = new AtomicInteger(0);
    private static int MAX_USER_CONNECTED = 30;


    public BinkServerSessionHandler(EventBus eventBus) {
        super(eventBus);
    }

    public BinkServerSessionHandler(SessionContext context, EventBus eventBus) {
        super(context, eventBus);
    }

    @Override
    public void channelActive(ChannelHandlerContext session) throws NoSuchAlgorithmException {
        final SessionContext sessionContext = new SessionContext(this.sessionContext);
        sessionContext.getLinksInfo().setCurLink(new FTNAddr(sessionContext.getStationConfig().getAddress()));
        sessionContext.setBusy(this.sessionContext.isBusy());
        sessionContext.setServerRole(ServerRole.SERVER);
        session.channel().attr(SessionKeys.SESSION_CONTEXT_KEY).set(sessionContext);
        session.channel().attr(SessionKeys.TRAFFIC_CRYPTER_KEY).set(new TrafficCrypter());
        synchronized (userConnected) {
            userConnected.set(userConnected.incrementAndGet());
        }
        if (!this.sessionContext.isBusy() && userConnected.get() > MAX_USER_CONNECTED) {
            this.sessionContext.setBusy(true);
        }
        sendGreeting(session, sessionContext);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext session) throws Exception {
        final SessionContext sessionContext = session.channel().attr(SessionKeys.SESSION_CONTEXT_KEY).get();
        if (sessionContext.getState().equals(SessionState.STATE_ERR) ||
                sessionContext.getState().equals(SessionState.STATE_BSY)) {
            log.warn("Client close with error: {}", sessionContext.getLastErrorMessage());
        }
        session.channel().attr(SessionKeys.SESSION_CONTEXT_KEY).set(null);
        synchronized (userConnected) {
            userConnected.set(userConnected.decrementAndGet());
        }
        if (this.sessionContext.isBusy() && userConnected.get() < MAX_USER_CONNECTED) {
            this.sessionContext.setBusy(false);
        }
    }

}
