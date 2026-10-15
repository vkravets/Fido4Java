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
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
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
public class Server implements AutoCloseable {

    public static final Logger logger = LoggerFactory.getLogger(Server.class);

    private int port;
    private TServer server;
    private ServerHandler serverHandler;
    private TNonblockingServerSocket transport;
    private Thread worker;
    private boolean stopped;

    private static final int DEFAULT_PORT = 9090;
    private static final String DEFAULT_BIND_IP = "127.0.0.1";

    public Server() {
        this(DEFAULT_PORT);
    }

    public Server(int port) {
        this(port, DEFAULT_BIND_IP, new DefaultServerHandler());
    }

    public Server(ServerHandler handler) {
        this(DEFAULT_PORT, DEFAULT_BIND_IP, handler);
    }

    public Server(int port, String bind_ip, ServerHandler handler) {
        this.port = port;
        this.serverHandler = handler;
        createServer(bind_ip);
    }

    private void createServer(String bind_ip) {
        Api.Processor<Api.Iface> apiProcessor = new Api.Processor<Api.Iface>(serverHandler.getApiHandler());
        LoginService.Processor<LoginService.Iface> loginProcessor = new LoginService.Processor<LoginService.Iface>(serverHandler.getLoginHandler());
        MessageService.Processor<MessageService.Iface> messageProcessor = new MessageService.Processor<MessageService.Iface>(serverHandler.getMessageHandler());

        TMultiplexedProcessor multiplexedProcessor = new TMultiplexedProcessor();
        multiplexedProcessor.registerProcessor("api", apiProcessor);
        multiplexedProcessor.registerProcessor("login", loginProcessor);
        if (serverHandler.getAuthHandler() != null) {
            multiplexedProcessor.registerProcessor("auth",
                                                   new AuthService.Processor<>(serverHandler.getAuthHandler()));
        }
        multiplexedProcessor.registerProcessor("messages", messageProcessor);
        if (serverHandler.getUiHandler() != null) {
            multiplexedProcessor.registerProcessor("ui",
                                                   new UiService.Processor<>(serverHandler.getUiHandler()));
        }
        StatisticService.Iface statistics = serverHandler.getStatisticsHandler();
        if (statistics != null) {
            multiplexedProcessor.registerProcessor("statistics",
                                                   new StatisticService.Processor<>(statistics));
        }

        try {
            transport = new TNonblockingServerSocket(new InetSocketAddress(bind_ip, this.port));
            TProtocolFactory factory = new TBinaryProtocol.Factory();
            THsHaServer.Args args = new THsHaServer.Args(transport);
            args.minWorkerThreads(2).maxWorkerThreads(8);
            args.maxReadBufferBytes = 2L * 1024 * 1024;
            args.processor(multiplexedProcessor).protocolFactory(factory);
            server = new THsHaServer(args);
        } catch (TTransportException e) {
            throw new IllegalStateException("Unable to bind RPC server to " + bind_ip + ":" + port,
                                            e);
        }
    }

    public synchronized void run() {
        if (stopped) {
            throw new IllegalStateException("RPC server has been stopped");
        }
        if (worker != null) {
            throw new IllegalStateException("RPC server is already started");
        }
        worker = new Thread(server::serve, "jftn-rpc");
        worker.start();
    }

    public int getLocalPort() {
        return transport.getPort();
    }

    public void stop() {
        Thread running;
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
            server.stop();
            transport.close();
            running = worker;
        }
        if (running != null && running != Thread.currentThread()) {
            try {
                running.join(5000);
                if (running.isAlive()) {
                    throw new IllegalStateException("RPC server did not stop");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping RPC server", e);
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}
