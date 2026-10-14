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

package org.fidonet.binkp.mina2;

import org.fidonet.binkp.common.ServerConnector;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.mina2.codec.BinkDataCodecFactory;
import org.fidonet.binkp.mina2.codec.TrafficCrypterCodecFilter;
import org.fidonet.binkp.mina2.commons.SessionKeys;
import org.fidonet.binkp.mina2.handler.BinkSessionHandler;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Server extends ServerConnector {

    private NioSocketAcceptor acceptor;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private int port = BINK_PORT;
    private final AtomicInteger userConnected = new AtomicInteger(0);
    private static int MAX_USER_CONNECTED = 30;

    private static Logger logger = LoggerFactory.getLogger(Server.class);

    public Server() {
        this(BINK_PORT);
    }

    public Server(int port) {
        this.port = port;
    }

    @Override
    public void run(final SessionContext context) throws Exception {
        if (acceptor != null) {
            throw new IllegalStateException("Server has already been started");
        }
        acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast("crypt", new TrafficCrypterCodecFilter());
        acceptor.getFilterChain()
                .addLast("codec", new ProtocolCodecFilter(new BinkDataCodecFactory()));
        acceptor.setHandler(new BinkSessionHandler(getEventBus()) {
            @Override
            public void sessionCreated(IoSession session) throws Exception {
                // got new connection to server
                SessionContext sessionContext = new SessionContext(context);
                int connected = userConnected.incrementAndGet();
                sessionContext.setBusy(context.isBusy() || connected > MAX_USER_CONNECTED);
                sessionContext.setServerRole(ServerRole.SERVER);
                session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, sessionContext);
                session.setAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY, new TrafficCrypter());
            }

            @Override
            public void sessionClosed(IoSession session) throws Exception {
                super.sessionClosed(session);
                SessionContext sessionContext = (SessionContext) session.getAttribute(SessionKeys.SESSION_CONTEXT_KEY);
                if (sessionContext == null) {
                    return;
                }
                if (sessionContext.getState() == SessionState.STATE_ERR
                        || sessionContext.getState() == SessionState.STATE_BSY) {
                    logger.warn("Client close with error: {}",
                                sessionContext.getLastErrorMessage());
                }
                session.removeAttribute(SessionKeys.SESSION_CONTEXT_KEY);
                userConnected.decrementAndGet();
            }
        });
        SocketAddress address = new InetSocketAddress(port);
        try {
            acceptor.bind(address);
        } catch (Exception failure) {
            stop();
            throw failure;
        }
    }

    @Override
    public void stop() {
        if (acceptor == null || !stopping.compareAndSet(false, true)) {
            return;
        }
        acceptor.unbind();
        acceptor.dispose();
    }

    public int getLocalPort() {
        if (acceptor == null || !acceptor.isActive()) {
            throw new IllegalStateException("Server is not listening");
        }
        return ((InetSocketAddress) acceptor.getLocalAddress()).getPort();
    }
}
