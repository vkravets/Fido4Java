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

package org.fidonet.binkp.netty.plugin.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/14/14
 * Time: 9:56 AM
 */

/**
 * This class control decrypt process for received data,
 * crypt process during sending data controls by last encoder
 */
public class TrafficCrypterCodec extends ByteToMessageCodec<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        TrafficCrypter trafficCrypter = ctx.channel().attr(SessionKeys.TRAFFIC_CRYPTER_KEY).get();
        byte[] array = new byte[msg.readableBytes()];
        msg.readBytes(array);
        trafficCrypter.encrypt(array, array.length);
        out.writeBytes(array);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() > 0) {
            TrafficCrypter trafficCrypter = ctx.channel().attr(SessionKeys.TRAFFIC_CRYPTER_KEY).get();
            byte[] array = new byte[in.readableBytes()];
            ByteBuf buf = Unpooled.buffer(array.length);
            in.readBytes(array);
            trafficCrypter.decrypt(array, array.length);
            buf.writeBytes(array);
            out.add(buf);
        }

    }
}
