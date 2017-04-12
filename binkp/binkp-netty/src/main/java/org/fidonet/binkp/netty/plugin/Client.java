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

package org.fidonet.binkp.netty.plugin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.fidonet.binkp.common.Connector;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.netty.plugin.commons.BinkPHandlerInitializer;
import org.fidonet.binkp.netty.plugin.handler.BinkSessionHandler;
import org.fidonet.types.Link;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/11/17
 * Time: 21:22
 */
public class Client extends Connector {
    private int connectionTimeout = 30 * 1000;

    private EventLoopGroup connector;
    private Channel session;
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

        connector = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .group(connector)
                .channel(NioSocketChannel.class)
                .handler(new BinkPHandlerInitializer(new BinkSessionHandler(sessionContext, getEventBus())));

        // Start the connection attempt.
        String hostname = link.getHostAddress();
        int port = link.getPort() != 0 ? link.getPort() : Connector.BINK_PORT;
        session = b.connect(hostname, port).sync().channel();
        try {
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
        if (session != null && session.isActive()) {
            session.close();
        }
        if (connector != null) {
            connector.shutdownGracefully();
        }
        connected = false;
    }

    public boolean isConnect() {
        return connected;
    }
}
