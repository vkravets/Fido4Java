package org.fidonet.binkp.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.netty.plugin.codec.*;
import org.fidonet.binkp.netty.plugin.commons.SessionKeys;
import org.fidonet.binkp.netty.plugin.handler.BinkServerSessionHandler;
import org.fidonet.binkp.netty.plugin.handler.BinkSessionHandler;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import org.fidonet.binkp.interop.AbstractJftnPeer;
import org.fidonet.binkp.interop.ExchangeFiles;

/** Netty-specific transport; shared checks live in AbstractJftnPeer. */
final class JftnTestPeer extends AbstractJftnPeer {
    private final EventLoopGroup workers = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final EventLoopGroup acceptor = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private volatile Channel connection;
    private Channel listener;

    JftnTestPeer(ServerRole role, boolean requestNr, ExchangeFiles files) {
        super(role, requestNr, files);
    }

    public void connect(String host, int port) throws Exception {
        session = template;
        session.getSendFiles().add(files.outgoingFromJftn());
        connection = new Bootstrap().group(workers).channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) {
                        configurePipeline(channel, new BinkSessionHandler(template, bus));
                    }
                }).connect(host, port).sync().channel();
    }

    public int listen() throws Exception {
        listener = new ServerBootstrap().group(acceptor, workers).channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
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

    private void configurePipeline(SocketChannel channel, ChannelHandler handler) {
        channel.pipeline().addLast(new TrafficCrypterCodec(), new BinkDataDecoder(), new BinkDataEncoder(),
                new ChannelDuplexHandler() {
                    @Override public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                        trace.incoming(message);
                        super.channelRead(ctx, message);
                    }
                    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                        trace.outgoing(message);
                        super.write(ctx, message, promise);
                    }
                }, handler);
    }

    @Override protected boolean awaitClosed() throws InterruptedException {
        return connection.closeFuture().await(30, TimeUnit.SECONDS);
    }

    @Override protected void closeTransport() {
        try {
            if (connection != null) connection.close().syncUninterruptibly();
            if (listener != null) listener.close().syncUninterruptibly();
        } finally {
            try { workers.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly(); }
            finally { acceptor.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly(); }
        }
    }
}
