package org.fidonet.binkp.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.fidonet.binkp.common.*;
import org.fidonet.binkp.common.config.*;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.events.*;
import org.fidonet.binkp.common.io.*;
import org.fidonet.binkp.netty.plugin.codec.*;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;
import org.fidonet.binkp.netty.plugin.handler.*;
import org.fidonet.events.EventBus;
import org.fidonet.types.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Runs the production handlers and codecs over real loopback TCP sockets. */
@RunWith(Parameterized.class)
public class NettyPeerTransferTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][]{
                {"ordinary",true,false,true,false},
                {"NR both directions",true,true,true,true},
                {"client requests NR",true,true,true,false},
                {"server requests NR",true,false,true,true},
                {"server disables NR",true,true,false,true},
                {"client disables NR",false,true,true,true}
        });
    }
    private final boolean clientEnabled, clientRequests, serverEnabled, serverRequests;
    public NettyPeerTransferTest(String name,boolean clientEnabled,boolean clientRequests,boolean serverEnabled,boolean serverRequests) {
        this.clientEnabled=clientEnabled;this.clientRequests=clientRequests;
        this.serverEnabled=serverEnabled;this.serverRequests=serverRequests;
    }
    private SessionContext context(String own,String peer,boolean enabled,boolean requested,ServerRole role) {
        StationConfig config=new StationConfig("TCP test","sysop","localhost","BINKP",own);
        config.setPartialDirectory(null);config.setNREnabled(enabled);config.setNRRequested(requested);
        Link link=new Link(new FTNAddr(peer),Collections.singletonList(new FTNAddr(own)),"tcp-secret","127.0.0.1",0);
        LinksInfo links=new LinksInfo(new ArrayList<>(Collections.singletonList(link)));links.setCurLink(link.getAddr());
        SessionContext context=new SessionContext(config,links);context.setServerRole(role);return context;
    }
    private FileData<java.io.InputStream> file(String name,byte[] bytes,long time) {
        return new FileData<>(new FileInfo(name,bytes.length,time),new ByteArrayInputStream(bytes));
    }
    @Test(timeout=30000) public void exchangesFilesInBothDirectionsThroughRealTcpConnection() throws Exception {
        byte[] clientBytes=new byte[20003],serverBytes=new byte[17009];
        new Random(11).nextBytes(clientBytes);new Random(12).nextBytes(serverBytes);
        SessionContext client=context("1:500/101","1:500/102",clientEnabled,clientRequests,ServerRole.CLIENT);
        SessionContext serverTemplate=context("1:500/102","1:500/101",serverEnabled,serverRequests,ServerRole.SERVER);
        client.getSendFiles().add(file("client file.pkt",clientBytes,123));
        EventBus clientBus=new EventBus(),serverBus=new EventBus();
        CountDownLatch received=new CountDownLatch(2);
        ConcurrentMap<String,byte[]> contents=new ConcurrentHashMap<>();
        AtomicReference<Throwable> eventFailure=new AtomicReference<>();
        FileReceivedEventHandler receiver=new FileReceivedEventHandler(){
            @Override public void onEventHandle(FileReceivedEvent event) {
                try {contents.put(event.getFile().getInfo().getName(),((ByteArrayOutputStream)event.getFile().getStream()).toByteArray());}
                catch(Throwable failure){eventFailure.compareAndSet(null,failure);}
                finally {received.countDown();}
            }
        };
        clientBus.subscribe(receiver);serverBus.subscribe(receiver);
        FrameTrace clientTrace=new FrameTrace(),serverTrace=new FrameTrace();
        AtomicReference<SessionContext> accepted=new AtomicReference<>();
        EventLoopGroup acceptor=new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()),
                serverWorkers=new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()),
                clientWorkers=new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        Channel listener=null,connection=null;
        try {
            listener=new ServerBootstrap().group(acceptor,serverWorkers).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>(){
                        @Override protected void initChannel(SocketChannel channel) {
                            pipeline(channel,serverTrace,new BinkServerSessionHandler(serverTemplate,serverBus){
                                @Override public void channelActive(ChannelHandlerContext channel) throws java.security.NoSuchAlgorithmException {
                                    super.channelActive(channel);
                                    SessionContext context=channel.channel().attr(SessionKeys.SESSION_CONTEXT_KEY).get();
                                    context.getSendFiles().add(file("server file.pkt",serverBytes,124));
                                    accepted.set(context);
                                }
                            });
                        }
                    }).bind("127.0.0.1",0).sync().channel();
            int port=((InetSocketAddress)listener.localAddress()).getPort();
            connection=new Bootstrap().group(clientWorkers).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,5000)
                    .handler(new ChannelInitializer<SocketChannel>(){
                        @Override protected void initChannel(SocketChannel channel) {
                            pipeline(channel,clientTrace,new BinkSessionHandler(client,clientBus));
                        }
                    }).connect("127.0.0.1",port).sync().channel();
            assertTrue("Both peers must receive a file",received.await(10,TimeUnit.SECONDS));
            assertNull("Receive event must complete without errors",eventFailure.get());
            assertArrayEquals(clientBytes,contents.get("client file.pkt"));
            assertArrayEquals(serverBytes,contents.get("server file.pkt"));
            assertTrue("The protocol must finish and close TCP",connection.closeFuture().await(10,TimeUnit.SECONDS));
            SessionContext server=accepted.get();assertNotNull(server);
            long completionDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while((!client.isTransferComplete() || !server.isTransferComplete()) && System.nanoTime()<completionDeadline)Thread.sleep(5);
            assertTrue("Client must receive EOB and all acknowledgements",client.isTransferComplete());
            assertTrue("Server must receive EOB and all acknowledgements",server.isTransferComplete());
            assertEquals(clientBytes.length,client.getSendFilesSize());assertEquals(serverBytes.length,server.getSendFilesSize());
            assertEquals(serverBytes.length,client.getRecvFilesSize());assertEquals(clientBytes.length,server.getRecvFilesSize());
            boolean clientSendsNr=clientEnabled && serverEnabled && serverRequests;
            boolean serverSendsNr=serverEnabled && clientEnabled && clientRequests;
            assertEquals(clientSendsNr,client.isNRMode());assertEquals(serverSendsNr,server.isNRMode());
            assertEquals(clientSendsNr ? -1 : 0,clientTrace.firstFileOffset());
            assertEquals(serverSendsNr ? -1 : 0,serverTrace.firstFileOffset());
            assertEquals(serverSendsNr,clientTrace.has(BinkCommand.M_GET));
            assertEquals(clientSendsNr,serverTrace.has(BinkCommand.M_GET));
            assertTrue(clientTrace.has(BinkCommand.M_GOT));assertTrue(serverTrace.has(BinkCommand.M_GOT));
            assertTrue(clientTrace.has(BinkCommand.M_EOB));assertTrue(serverTrace.has(BinkCommand.M_EOB));
        } finally {
            if(connection!=null)connection.close().syncUninterruptibly();
            if(listener!=null)listener.close().syncUninterruptibly();
            clientWorkers.shutdownGracefully(0,5,TimeUnit.SECONDS).syncUninterruptibly();
            serverWorkers.shutdownGracefully(0,5,TimeUnit.SECONDS).syncUninterruptibly();
            acceptor.shutdownGracefully(0,5,TimeUnit.SECONDS).syncUninterruptibly();
            clientBus.unsubscribe(receiver);serverBus.unsubscribe(receiver);
            clientBus.shutdown();serverBus.shutdown();
        }
    }
    private static void pipeline(SocketChannel channel,FrameTrace trace,ChannelHandler handler) {
        channel.pipeline().addLast(new TrafficCrypterCodec(),new BinkDataDecoder(),new BinkDataEncoder(),trace,handler);
    }
    private static class FrameTrace extends ChannelDuplexHandler {
        private final List<BinkFrame> frames=new CopyOnWriteArrayList<>();
        @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise) throws Exception {
            if(message instanceof BinkFrame){BinkFrame frame=(BinkFrame)message;frames.add(new BinkFrame(frame.getDataInfo(),frame.getData().clone()));}
            super.write(context,message,promise);
        }
        boolean has(BinkCommand command) {
            return frames.stream().anyMatch(frame->frame.getDataInfo()<0 && frame.getData()[0]==command.getCmd());
        }
        long firstFileOffset() throws Exception {
            for(BinkFrame frame:frames)if(frame.getDataInfo()<0 && frame.getData()[0]==BinkCommand.M_FILE.getCmd())
                return FileInfo.parseFileInfo(new String(frame.getData(),StandardCharsets.UTF_8).substring(1)).getOffset();
            throw new AssertionError("No M_FILE sent");
        }
    }
}
