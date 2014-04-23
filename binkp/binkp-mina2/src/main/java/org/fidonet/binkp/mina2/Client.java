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

package org.fidonet.binkp.mina2;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.fidonet.binkp.common.Connector;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.mina2.codec.BinkDataCodecFactory;
import org.fidonet.binkp.mina2.codec.TrafficCrypterCodecFilter;
import org.fidonet.binkp.mina2.handler.BinkSessionHandler;
import org.fidonet.types.Link;

import java.net.InetSocketAddress;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Client extends Connector {
    private long connectionTimeout = 30 * 1000L;

    private NioSocketConnector connector;
    private IoSession session;
    private Link link;

    private boolean connected = false;

    public Client(Link link) {
        this.link = link;
    }

    public Client(Link link, long connectionTimeout) {
        this(link);
        this.connectionTimeout = connectionTimeout;
    }

    public void run(SessionContext sessionContext) throws Exception {
        sessionContext.setServerRole(ServerRole.CLIENT);
        connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(connectionTimeout);
        connector.getFilterChain().addLast("crypt", new TrafficCrypterCodecFilter());
        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new BinkDataCodecFactory()));
        connector.setHandler(new BinkSessionHandler(sessionContext, getEventBus()));
        String hostname = link.getHostAddress();
        int port = link.getPort() != 0 ? link.getPort() : Connector.BINK_PORT;
        ConnectFuture connection = connector.connect(new InetSocketAddress(hostname, port));
        connection.awaitUninterruptibly();
        connected = connection.isConnected();
        if (connected) {
            session = connection.getSession();
        } else {
            System.out.println("Cannot connect to target host");
        }

    }

    public void stop() {
        if (session != null) {
            session.getCloseFuture().awaitUninterruptibly();
        }
        if (connector != null) {
            connector.dispose();
        }
    }

    public boolean isConnect() {
        return connected;
    }
}
