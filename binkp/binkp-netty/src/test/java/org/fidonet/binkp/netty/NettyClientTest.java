/*
 * Copyright (c) 2012-2018, Vladimir Kravets
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

package org.fidonet.binkp.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.fidonet.binkp.common.Connector;
import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.events.*;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.netty.plugin.codec.BinkDataDecoder;
import org.fidonet.binkp.netty.plugin.codec.BinkDataEncoder;
import org.fidonet.binkp.netty.plugin.codec.TrafficCrypterCodec;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;
import org.fidonet.binkp.netty.plugin.handler.BinkSessionHandler;
import org.fidonet.events.EventHandler;
import org.fidonet.events.HasEventBus;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/26/12
 * Time: 4:57 PM
 */
@Ignore
public class NettyClientTest extends HasEventBus {

    private SessionContext sessionContext;

    @Before
    public void setUp() throws Exception {
        final StationConfig config = new StationConfig("Test Station", "Vasya Pupkin", "Odessa, Ukraine", "BINKP", "2:467/110.113");
        config.setCryptMode(true);
        final FTNAddr node = new FTNAddr("2:467/110");
        LinksInfo linksInfo = new LinksInfo(
                new LinkedList<Link>() {
                    {
                        add(new Link(node,
                                        new LinkedList<FTNAddr>() {
                                            {
                                                add(new FTNAddr("2:467/110.113"));
                                            }
                                        },
                                        "pass_i_f", "localhost", 24554
                                ).withMD(true)
                        );
                    }
                }
        );
        linksInfo.setCurLink(node);
        sessionContext = new SessionContext(config, linksInfo);
    }

    private class ClientMock extends Connector implements Runnable {

        private EmbeddedChannel session;
        private SessionContext sessionContext;

        public ClientMock(SessionContext sessionContext) {
            this.sessionContext = sessionContext;
        }

        @Override
        public void run(SessionContext sessionContext) throws Exception {
            sessionContext.setServerRole(ServerRole.CLIENT);
            session = new EmbeddedChannel();
            BinkDataDecoder decoder = new BinkDataDecoder();
            decoder.setSingleDecode(true);
            session.pipeline().addLast(new TrafficCrypterCodec())
                    .addLast(decoder)
                    .addLast(new BinkDataEncoder())
                    .addLast(new BinkSessionHandler(sessionContext, getEventBus()));
            sessionContext.setState(SessionState.STATE_IDLE);
            session.attr(SessionKeys.SESSION_CONTEXT_KEY).set(sessionContext);
            session.attr(SessionKeys.TRAFFIC_CRYPTER_KEY).set(new TrafficCrypter());
        }

        @Override
        public void stop() {
            session.close();
        }

        public EmbeddedChannel getSession() {
            return session;
        }

        public void writeStreamToSession(InputStream stream) throws Exception {
            int bufSize = 5;
            int size = 0;
            byte[] bytes = new byte[bufSize];
            while ((size = stream.read(bytes)) != -1) {
                ByteBuf buf = Unpooled.buffer(size);
                buf.writeBytes(bytes, 0, size);
                session.writeInbound(buf);
                session.readInbound();
                Thread.sleep(20);
            }
            Thread.sleep(1000);
            sessionContext.setState(SessionState.STATE_END);
            session.finishAndReleaseAll();
        }

        @Override
        public void run() {
            try {
                run(sessionContext);
                InputStream recvRawData = ClientMock.class.getClassLoader().getResourceAsStream("recv_stream.bin");
                ClientMock.this.writeStreamToSession(recvRawData);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stop();
            }
        }

        public void registerEvent(EventHandler eventHandler) {
            getEventBus().subscribe(eventHandler);
        }
    }

    private Boolean connected = false;
    private Boolean disconnected = false;
    private Boolean fileReceived = false;
    private DisconnectedEventHandler disconnectedEventHandler;
    private ConnectedEventHandler connectedEventHandler;
    private FileReceivedEventHandler fileReceivedEventHandler;

    @Test
    public void testBaseFlow() throws InterruptedException {
        sessionContext.getStationConfig().setCryptMode(true);
        ClientMock clientMock = new ClientMock(sessionContext);
        registerEvents(clientMock);
        Thread thread = new Thread(clientMock);
        thread.start();
        thread.join();
        FileInfo fileInfo = sessionContext.getRecvFiles().peek().getInfo();
        Assert.assertEquals("0000FF8F.WE5", fileInfo.getName());
        Assert.assertEquals(766, fileInfo.getSize());
        Assert.assertEquals(true, fileInfo.isFinished());
        Assert.assertEquals(true, connected);
        Assert.assertEquals(true, disconnected);
        Assert.assertEquals(true, fileReceived);
        clientMock.stop();
        Thread.sleep(1000);
    }

    private void registerEvents(ClientMock client) {
        connectedEventHandler = new ConnectedEventHandler() {
            @Override
            public void onEventHandle(ConnectedEvent event) {
                connected = true;
            }
        };
        client.registerEvent(connectedEventHandler);

        disconnectedEventHandler = new DisconnectedEventHandler() {
            @Override
            public void onEventHandle(DisconnectedEvent event) {
                disconnected = true;
            }
        };
        client.registerEvent(disconnectedEventHandler);

        fileReceivedEventHandler = new FileReceivedEventHandler() {
            @Override
            public void onEventHandle(FileReceivedEvent event) {
                fileReceived = true;
                FileInfo expectedInfo = new FileInfo("0000FF8F.WE5", 766, 1348664500L);
                Assert.assertEquals(expectedInfo, event.getFile().getInfo());
                Assert.assertEquals(true, event.getFile().getInfo().isFinished());
                ByteArrayOutputStream outStream = (ByteArrayOutputStream) event.getFile().getStream();
                Assert.assertEquals(766, outStream.toByteArray().length);
            }
        };
        client.registerEvent(fileReceivedEventHandler);
    }

}
