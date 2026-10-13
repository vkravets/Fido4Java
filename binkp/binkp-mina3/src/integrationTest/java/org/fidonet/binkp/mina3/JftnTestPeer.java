package org.fidonet.binkp.mina3;

import org.apache.mina.api.AbstractIoFilter;
import org.apache.mina.api.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filterchain.ReadFilterChainController;
import org.apache.mina.filterchain.WriteFilterChainController;
import org.apache.mina.session.WriteRequest;
import org.apache.mina.transport.nio.NioTcpClient;
import org.apache.mina.transport.nio.NioTcpServer;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.interop.AbstractJftnPeer;
import org.fidonet.binkp.interop.ExchangeFiles;
import org.fidonet.binkp.mina3.codec.BinkDataDecoder;
import org.fidonet.binkp.mina3.codec.BinkDataEncoder;
import org.fidonet.binkp.mina3.codec.TrafficCrypterCodecFilter;
import org.fidonet.binkp.mina3.handler.BinkSessionHandler;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

final class JftnTestPeer extends AbstractJftnPeer {
    private NioTcpClient connector;
    private NioTcpServer acceptor;
    private volatile IoSession connection;
    private final CountDownLatch closed = new CountDownLatch(1);

    JftnTestPeer(ServerRole role, boolean requestNr, ExchangeFiles files) {
        super(role, requestNr, files);
    }

    @Override public void connect(String host, int port) throws Exception {
        connector = new NioTcpClient();
        connector.setConnectTimeoutMillis(5000);
        configure(connector);
        connection = connector.connect(new InetSocketAddress(host, port)).get(10, TimeUnit.SECONDS);
    }

    @Override public int listen() throws Exception {
        acceptor = new NioTcpServer();
        configure(acceptor);
        acceptor.bind(new InetSocketAddress("127.0.0.1", 0));
        return ((InetSocketAddress) acceptor.getServerSocketChannel().getLocalAddress()).getPort();
    }

    private void configure(org.apache.mina.api.IoService service) {
        ProtocolCodecFilter<BinkFrame, ByteBuffer, Void, BinkDataDecoder.Context> codec =
                new ProtocolCodecFilter<>(new BinkDataEncoder<BinkFrame>(), new BinkDataDecoder());
        service.setFilters(new TrafficCrypterCodecFilter(), codec, new AbstractIoFilter() {
            @Override public void messageReceived(IoSession io, Object message, ReadFilterChainController controller) {
                trace.incoming(message);
                super.messageReceived(io, message, controller);
            }
            @Override public void messageWriting(IoSession io, WriteRequest request, WriteFilterChainController controller) {
                trace.outgoing(request.getMessage());
                super.messageWriting(io, request, controller);
            }
        });
        service.setIoHandler(new BinkSessionHandler(template, bus) {
            @Override public void sessionOpened(IoSession io) {
                session = template;
                session.getSendFiles().add(files.outgoingFromJftn());
                connection = io;
                super.sessionOpened(io);
                accepted.countDown();
            }
            @Override public void sessionClosed(IoSession io) {
                try { super.sessionClosed(io); }
                finally { closed.countDown(); }
            }
        });
    }

    @Override protected boolean awaitClosed() throws InterruptedException {
        return closed.await(30, TimeUnit.SECONDS);
    }

    @Override protected void closeTransport() throws Exception {
        try {
            if (connection != null && closed.getCount() != 0) connection.close(true).get(5, TimeUnit.SECONDS);
        } finally {
            try { if (connector != null) connector.disconnect(); }
            finally { if (acceptor != null) acceptor.unbind(); }
        }
    }
}
