package org.fidonet.binkp.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.events.FileReceivedEventHandler;
import org.fidonet.binkp.netty.plugin.codec.*;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;
import org.fidonet.binkp.netty.plugin.handler.BinkServerSessionHandler;
import org.fidonet.binkp.netty.plugin.handler.BinkSessionHandler;
import org.fidonet.events.EventBus;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Owns JFtn sessions, event reception and Netty resources for either role. */
final class JftnTestPeer implements AutoCloseable {
    private final ExchangeFiles files;
    private final boolean requestNr;
    private final SessionContext template;
    private final EventBus bus = new EventBus();
    private final EventLoopGroup workers = new NioEventLoopGroup(1);
    private final EventLoopGroup acceptor = new NioEventLoopGroup(1);
    private final ProtocolTrace trace = new ProtocolTrace();
    private final CountDownLatch accepted = new CountDownLatch(1);
    private final ReceiveProbe receiver = new ReceiveProbe();
    private volatile SessionContext session;
    private volatile Channel connection;
    private Channel listener;

    JftnTestPeer(ServerRole role, boolean requestNr, ExchangeFiles files) {
        this.files = files;
        this.requestNr = requestNr;
        template = createContext(role, requestNr);
        bus.subscribe(receiver);
    }

    void connect(String host, int port) throws Exception {
        session = template;
        session.getSendFiles().add(files.outgoingFromJftn());
        connection = new Bootstrap().group(workers).channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) {
                        configurePipeline(channel, new BinkSessionHandler(template, bus));
                    }
                }).connect(host, port).sync().channel();
    }

    int listen() throws Exception {
        listener = new ServerBootstrap().group(acceptor, workers).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) {
                        configurePipeline(channel, new BinkServerSessionHandler(template, bus) {
                            @Override public void channelActive(ChannelHandlerContext context) throws NoSuchAlgorithmException {
                                super.channelActive(context);
                                session = channel.attr(SessionKeys.SESSION_CONTEXT_KEY).get();
                                session.getSendFiles().add(files.outgoingFromJftn());
                                connection = channel;
                                accepted.countDown();
                            }
                        });
                    }
                }).bind("127.0.0.1", 0).sync().channel();
        return ((InetSocketAddress) listener.localAddress()).getPort();
    }

    void awaitConnection() throws InterruptedException {
        assertTrue("binkd must connect to JFtn", accepted.await(30, TimeUnit.SECONDS));
    }

    void assertReceivedFile() throws InterruptedException {
        assertTrue("binkd must send its file within 30 seconds", receiver.received.await(30, TimeUnit.SECONDS));
        assertNull("Receive event failed", receiver.failure.get());
        assertArrayEquals(files.toJftn, receiver.bytes.get());
    }

    void assertSessionFinished() throws InterruptedException {
        assertTrue("TCP session must close normally", connection.closeFuture().await(30, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!session.isTransferComplete() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue("EOB and all file acknowledgements are required", session.isTransferComplete());
    }

    void assertProtocol() throws Exception {
        trace.assertExchange(requestNr);
    }

    void saveDiagnostics(Path artifacts) {
        try {
            Files.writeString(artifacts.resolve("protocol.log"), trace.log());
        } catch (Exception failure) {
            System.err.println("Could not save JFtn diagnostics: " + failure);
        }
    }

    private void configurePipeline(SocketChannel channel, ChannelHandler handler) {
        channel.pipeline().addLast(new TrafficCrypterCodec(), new BinkDataDecoder(),
                new BinkDataEncoder(), trace, handler);
    }

    private static SessionContext createContext(ServerRole role, boolean requestNr) {
        StationConfig config = new StationConfig("JFtn integration test", "Test", "localhost", "BINKP", "1:500/101");
        config.setPartialDirectory(null);
        config.setNREnabled(true);
        config.setNRRequested(requestNr);
        Link link = new Link(new FTNAddr("1:500/102"), Collections.singletonList(new FTNAddr("1:500/101")),
                "docker-secret", "127.0.0.1", 0);
        LinksInfo links = new LinksInfo(new ArrayList<>(Collections.singletonList(link)));
        links.setCurLink(link.getAddr());
        SessionContext context = new SessionContext(config, links);
        context.setServerRole(role);
        return context;
    }

    @Override public void close() {
        try {
            if (connection != null) connection.close().syncUninterruptibly();
            if (listener != null) listener.close().syncUninterruptibly();
        } finally {
            try {
                workers.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            } finally {
                try {
                    acceptor.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
                } finally {
                    bus.unsubscribe(receiver);
                    bus.shutdown();
                }
            }
        }
    }

    private static final class ReceiveProbe extends FileReceivedEventHandler {
        final CountDownLatch received = new CountDownLatch(1);
        final AtomicReference<byte[]> bytes = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override public void onEventHandle(FileReceivedEvent event) {
            try {
                assertEquals(ExchangeFiles.FROM_BINKD, event.getFile().getInfo().getName());
                bytes.set(((ByteArrayOutputStream) event.getFile().getStream()).toByteArray());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                received.countDown();
            }
        }
    }
}
