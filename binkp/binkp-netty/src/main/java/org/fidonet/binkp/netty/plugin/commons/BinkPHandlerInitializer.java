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

import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.fidonet.binkp.netty.plugin.codec.BinkDataDecoder;
import org.fidonet.binkp.netty.plugin.codec.BinkDataEncoder;
import org.fidonet.binkp.netty.plugin.codec.TrafficCrypterCodec;
import org.fidonet.binkp.netty.plugin.handler.BinkServerSessionHandler;


/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/11/17
 * Time: 21:35
 */
public class BinkPHandlerInitializer extends ChannelInitializer<SocketChannel> {

    private ChannelInboundHandler sessionHandler;

    public BinkPHandlerInitializer(ChannelInboundHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new TrafficCrypterCodec());
        pipeline.addLast(new BinkDataDecoder());
        pipeline.addLast(new BinkDataEncoder());
        pipeline.addLast(sessionHandler);
    }
}
