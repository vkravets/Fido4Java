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

package org.fidonet.binkp;

import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.fidonet.binkp.codec.BinkDataCodecFactory;
import org.fidonet.binkp.config.ServerRole;
import org.fidonet.binkp.handler.BinkSessionHandler;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Server extends Connector {

    private NioSocketAcceptor acceptor;
    private int port = Connector.BINK_PORT;
    private final AtomicReference<Integer> userConnected = new AtomicReference<Integer>(0);
    private static int MAX_USER_CONNECTED = 30;

    private static ILogger logger = LoggerFactory.getLogger(Server.class);

    public Server() {
    }

    public Server(int port) {
        this.port = port;
    }

    @Override
    public void run(final SessionContext context) throws Exception {
        acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new BinkDataCodecFactory()));
        acceptor.setHandler(new BinkSessionHandler(getEventBus()));
        acceptor.addListener(new IoServiceListener() {
            @Override
            public void serviceActivated(IoService ioService) throws Exception {
            }

            @Override
            public void serviceIdle(IoService ioService, IdleStatus idleStatus) throws Exception {
            }

            @Override
            public void serviceDeactivated(IoService ioService) throws Exception {
                Server.this.stop();
            }

            @Override
            public void sessionCreated(IoSession session) throws Exception {
                // got new connection to server
                SessionContext sessionContext = new SessionContext(context);
                sessionContext.setBusy(context.isBusy());
                sessionContext.setServerRole(ServerRole.SERVER);
                session.setAttribute(SessionContext.SESSION_CONTEXT_KEY, sessionContext);
                //session.getRemoteAddress()
                synchronized (userConnected.get()) {
                    userConnected.set(userConnected.get() + 1);
                }
                if (!context.isBusy() && userConnected.get() > MAX_USER_CONNECTED) {
                    synchronized (context) {
                        context.setBusy(true);
                    }
                }
            }

            @Override
            public void sessionDestroyed(IoSession session) throws Exception {
                SessionContext sessionContext = (SessionContext) session.getAttribute(SessionContext.SESSION_CONTEXT_KEY);
                if (sessionContext.getState().equals(SessionState.STATE_ERR) ||
                        sessionContext.getState().equals(SessionState.STATE_BSY)) {
                    logger.warn("Client close with error: " + sessionContext.getLastErrorMessage());
                }
                session.removeAttribute(SessionContext.SESSION_CONTEXT_KEY);
                synchronized (userConnected) {
                    userConnected.set(userConnected.get() - 1);
                }
                if (context.isBusy() && userConnected.get() < MAX_USER_CONNECTED) {
                    synchronized (context) {
                        context.setBusy(false);
                    }
                }
            }
        });
        SocketAddress address = new InetSocketAddress(port);
        acceptor.bind(address);
    }

    @Override
    public void stop() {
        acceptor.unbind();
        acceptor.dispose();
    }
}
