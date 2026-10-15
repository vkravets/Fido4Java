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

package org.fidonet.binkp.netty.plugin.commons;

import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.io.FilesSender;
import org.fidonet.binkp.common.protocol.Session;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/11/17
 * Time: 23:12
 */
public class NettySession implements Session {

    private ChannelHandlerContext context;
    private volatile ChannelFuture lastWrite;

    public NettySession(ChannelHandlerContext context) {
        this.context = context;
    }

    @Override
    public synchronized void write(Object message) {
        lastWrite = context.channel()
                           .writeAndFlush(message)
                           .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    public void awaitWrites() throws IOException, InterruptedException {
        ChannelFuture pending = lastWrite;
        if (pending == null) {
            return;
        }
        if (!pending.isDone() && context.executor().inEventLoop()) {
            throw new IOException("Cannot wait for file writes on the Netty event loop");
        }
        if (!pending.await(30, TimeUnit.SECONDS)) {
            context.close();
            throw new IOException("Timed out writing to the peer");
        }
        if (!pending.isSuccess()) {
            throw new IOException("Unable to write to the peer", pending.cause());
        }
    }

    @Override
    public void close(boolean close) {
        if (close) {
            context.channel().close();
        } else {
            // Enqueue behind writes from the sender thread, including the final M_EOB.
            context.executor()
                   .execute(() -> context.channel()
                                         .writeAndFlush(Unpooled.EMPTY_BUFFER)
                                         .addListener(ChannelFutureListener.CLOSE));
        }
    }

    @Override
    public FilesSender getFileSender() {
        return context.channel().attr(SessionKeys.FILESENDER_KEY).get();
    }

    @Override
    public void setFileSender(FilesSender filesSender) {
        context.channel().attr(SessionKeys.FILESENDER_KEY).set(filesSender);
    }

    @Override
    public TrafficCrypter getTrafficCrypter() {
        return context.channel().attr(SessionKeys.TRAFFIC_CRYPTER_KEY).get();
    }

    @Override
    public void setTrafficCrypter(TrafficCrypter trafficCrypter) {
        context.channel().attr(SessionKeys.TRAFFIC_CRYPTER_KEY).set(trafficCrypter);
    }
}
