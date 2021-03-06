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

package org.fidonet.binkp.mina3;

import org.apache.mina.api.IoFuture;
import org.apache.mina.api.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.nio.NioTcpClient;
import org.fidonet.binkp.common.ClientConnector;
import org.fidonet.binkp.common.Connector;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.mina3.codec.BinkDataDecoder;
import org.fidonet.binkp.mina3.codec.BinkDataEncoder;
import org.fidonet.binkp.mina3.codec.TrafficCrypterCodecFilter;
import org.fidonet.binkp.mina3.handler.BinkSessionHandler;
import org.fidonet.types.Link;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Client extends ClientConnector {
    private int connectionTimeout = 30 * 1000;

    private NioTcpClient connector;
    private IoSession session;
    private Link link;

    private boolean connected = false;

    public Client(Link link) {
        this.link = link;
    }

    public Client(Link link, int connectionTimeout) {
        this(link);
        this.connectionTimeout = connectionTimeout;
    }

    public void run(final SessionContext sessionContext) throws Exception {
        sessionContext.setServerRole(ServerRole.CLIENT);
        connector = new NioTcpClient();
        connector.setConnectTimeoutMillis(connectionTimeout);
        final TrafficCrypterCodecFilter crypterFilter = new TrafficCrypterCodecFilter();
        final ProtocolCodecFilter<BinkFrame, ByteBuffer, Void, BinkDataDecoder.Context> bikpProtocolFilter = new ProtocolCodecFilter<BinkFrame, ByteBuffer, Void, BinkDataDecoder.Context>(new BinkDataEncoder<BinkFrame>(), new BinkDataDecoder());
        connector.setFilters(crypterFilter, bikpProtocolFilter);
        connector.setIoHandler(new BinkSessionHandler(sessionContext, getEventBus()));
        final String hostname = link.getHostAddress();
        final int port = link.getPort() != 0 ? link.getPort() : Connector.BINK_PORT;
        IoFuture<IoSession> connection = connector.connect(new InetSocketAddress(hostname, port));
        try {
            session = connection.get();
            sessionContext.getLinksInfo().setCurLink(link.getAddr());
            sessionContext.setState(SessionState.STATE_IDLE);
            connected = true;
        } catch (Exception ex) {
            connected = false;
            ex.printStackTrace();
            System.out.println("Cannot connect to target host");
        }


    }

    public void stop() {
        if (session != null && !(session.isClosing() || session.isClosed())) {
            final IoFuture<Void> close = session.close(true);
            try {
                close.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        if (connector != null) {
            try {
                connector.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        connected = false;
    }

    public boolean isConnect() {
        return connected;
    }
}
