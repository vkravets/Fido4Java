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

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.commands.BSYCommand;
import org.fidonet.binkp.common.commands.ERRCommand;
import org.fidonet.binkp.common.commands.share.Command;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.protocol.BinkpSessionEngine;
import org.fidonet.binkp.netty.plugin.commons.NettySession;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;
import org.fidonet.events.EventBus;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DuplexChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:18 PM
 */
public class BinkSessionHandler extends SimpleChannelInboundHandler<BinkFrame> {

    protected static final Logger log = LoggerFactory.getLogger(BinkSessionHandler.class.getName());

    protected SessionContext sessionContext;
    private EventBus eventBus;

    public BinkSessionHandler(EventBus eventBus) {
        this.sessionContext = null;
        if (eventBus == null) {
            throw new UnsupportedOperationException();
        }
        this.eventBus = eventBus;
    }

    public BinkSessionHandler(SessionContext context, EventBus eventBus) {
        this(eventBus);
        this.sessionContext = context;
    }

    private SessionContext getSessionContext(ChannelHandlerContext session) {
        return session.channel().attr(SessionKeys.SESSION_CONTEXT_KEY).get();
    }

    @Override
    public void channelActive(ChannelHandlerContext session) throws NoSuchAlgorithmException {
        session.channel().attr(SessionKeys.SESSION_CONTEXT_KEY).set(sessionContext);
        session.channel().attr(SessionKeys.TRAFFIC_CRYPTER_KEY).set(new TrafficCrypter());

        SessionContext sessionContext = getSessionContext(session);
        log.info("binkp TCP connection opened: remote={}, role={}",
                 session.channel().remoteAddress(),
                 sessionContext.getServerRole());

        sendGreeting(session, sessionContext);
    }

    protected void sendGreeting(ChannelHandlerContext session, SessionContext sessionContext)
            throws NoSuchAlgorithmException {
        sessionContext.prepareTransfer();
        sessionContext.setReceiveEventBus(eventBus);
        boolean isClient = sessionContext.getServerRole().equals(ServerRole.CLIENT);

        NettySession nettySession = new NettySession(session);

        if (!isClient && sessionContext.isBusy()) {
            log.info("Server is busy. Sending BSY command...");
            Command bsy = new BSYCommand();
            sessionContext.setLastErrorMessage("To many connections");
            try {
                bsy.send(nettySession, sessionContext);
            } catch (Exception e) {
                log.warn("Node is busy");
            }
            sessionContext.setState(SessionState.STATE_BSY);
            // Half-close output after flushing BSY. Closing both directions while
            // the peer is still sending its greeting can reset TCP and lose BSY.
            if (session.channel() instanceof DuplexChannel) {
                session.channel()
                       .writeAndFlush(Unpooled.EMPTY_BUFFER)
                       .addListener(future -> ((DuplexChannel) session.channel()).shutdownOutput());
                ScheduledFuture<?> timeout = session.executor()
                                                    .schedule(() -> session.close(),
                                                              5,
                                                              TimeUnit.SECONDS);
                session.channel().closeFuture().addListener(future -> timeout.cancel(false));
            } else {
                nettySession.close(false);
            }
            return;
        }
        BinkpSessionEngine.greet(nettySession, sessionContext);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinkFrame data) throws Exception {
        SessionContext sessionContext = getSessionContext(ctx);
        if (sessionContext.getState() == SessionState.STATE_BSY
                && sessionContext.getServerRole() == ServerRole.SERVER) {
            return;
        }
        NettySession nettySession = new NettySession(ctx);
        try {
            BinkpSessionEngine.receive(nettySession, sessionContext, data);
        } catch (Exception failure) {
            sessionContext.fail(failure);
            try {
                new ERRCommand().send(nettySession, sessionContext);
            } catch (Exception writeFailure) {
                failure.addSuppressed(writeFailure);
            }
            sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
            exceptionCaught(ctx, failure);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SessionContext sessionContext = getSessionContext(ctx);
        log.error(cause.getMessage(), cause);
        sessionContext.fail(cause);
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        SessionContext context = getSessionContext(ctx);
        if (context != null) {
            if (context.getState() != SessionState.STATE_ERR
                    && context.getState() != SessionState.STATE_BSY) {
                context.setState(SessionState.STATE_END);
            }
            context.closeReceiveStreams();
        }
        log.info("binkp TCP connection closed: remote={}, state={}",
                 ctx.channel().remoteAddress(),
                 context == null ? "unknown" : context.getState());
        super.channelInactive(ctx);
    }
}
