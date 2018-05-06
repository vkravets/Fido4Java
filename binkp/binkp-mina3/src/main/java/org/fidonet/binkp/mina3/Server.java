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

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.nio.NioTcpServer;
import org.fidonet.binkp.common.ServerConnector;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.mina3.codec.BinkDataDecoder;
import org.fidonet.binkp.mina3.codec.BinkDataEncoder;
import org.fidonet.binkp.mina3.codec.TrafficCrypterCodecFilter;
import org.fidonet.binkp.mina3.handler.BinkServerSessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Server extends ServerConnector {

    private NioTcpServer acceptor;
    private int port = BINK_PORT;

    private static Logger logger = LoggerFactory.getLogger(Server.class);

    public Server() {
        this(BINK_PORT);
    }

    public Server(int port) {
        this.port = port;
    }

    @Override
    public void run(final SessionContext context) throws Exception {
        acceptor = new NioTcpServer();
        TrafficCrypterCodecFilter crypterFilter = new TrafficCrypterCodecFilter();
        ProtocolCodecFilter<BinkFrame, ByteBuffer, Void, BinkDataDecoder.Context> bikpProtocolFilter = new ProtocolCodecFilter<BinkFrame, ByteBuffer, Void, BinkDataDecoder.Context>(new BinkDataEncoder<BinkFrame>(), new BinkDataDecoder());
        acceptor.setFilters(crypterFilter, bikpProtocolFilter);
        acceptor.setIoHandler(new BinkServerSessionHandler(context, getEventBus()));
        SocketAddress address = new InetSocketAddress(port);
        acceptor.bind(address);
    }

    @Override
    public void stop() {
        acceptor.unbind();
    }
}
