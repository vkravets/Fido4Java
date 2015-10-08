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

package org.fidonet.jftn.rpc;

import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/24/14
 * Time: 11:29 AM
 */
public class Server {

    public static final Logger logger = LoggerFactory.getLogger(Server.class);

    private int port;
    private TServer server;
    private ServerHandler serverHandler;

    private static final int DEFAULT_PORT = 9090;

    public Server() {
        this(DEFAULT_PORT);
    }

    public Server(int port) {
        this(port, new DefaultServerHandler());
    }

    public Server(ServerHandler handler) {
        this(DEFAULT_PORT, handler);
    }

    public Server(int port, ServerHandler handler) {
        this.port = port;
        this.serverHandler = handler;
        createServer();
    }

    private void createServer() {
        Api.Processor<Api.Iface> apiProcessor = new Api.Processor<Api.Iface>(serverHandler.getApiHandler());
        LoginService.Processor<LoginService.Iface> loginProcessor = new LoginService.Processor<LoginService.Iface>(serverHandler.getLoginHandler());
        MessageService.Processor<MessageService.Iface> messageProcessor = new MessageService.Processor<MessageService.Iface>(serverHandler.getMessageHandler());
        StatisticService.Processor<StatisticService.Iface> statisticProcessor = new StatisticService.Processor<StatisticService.Iface>(serverHandler.getStatisticsHandler());

        TMultiplexedProcessor multiplexedProcessor = new TMultiplexedProcessor();
        multiplexedProcessor.registerProcessor("api", apiProcessor);
        multiplexedProcessor.registerProcessor("login", loginProcessor);
        multiplexedProcessor.registerProcessor("messages", messageProcessor);
        multiplexedProcessor.registerProcessor("statistics", statisticProcessor);

        try {
            TNonblockingServerTransport transport = new TNonblockingServerSocket(new InetSocketAddress("127.0.0.1", this.port));
            TProtocolFactory factory = new TBinaryProtocol.Factory();
            server = new TNonblockingServer(new TNonblockingServer.Args(transport).processor(multiplexedProcessor).protocolFactory(factory));
        } catch (TTransportException e) {
            logger.error(e.getMessage(), e);
        }
    }


    public void run() {
        Runnable worker = new Runnable() {
            @Override
            public void run() {
                server.serve();
            }
        };
        new Thread(worker).start();
    }

    public void stop() {
        if (server != null && server.isServing()) {
            server.stop();
        }
    }


}
