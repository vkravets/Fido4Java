package org.fidonet.binkp.mina2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.interop.AbstractJftnPeer;
import org.fidonet.binkp.interop.ExchangeFiles;
import org.fidonet.binkp.mina2.codec.BinkDataCodecFactory;
import org.fidonet.binkp.mina2.codec.TrafficCrypterCodecFilter;
import org.fidonet.binkp.mina2.handler.BinkSessionHandler;

import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

final class JftnTestPeer extends AbstractJftnPeer {
    private NioSocketConnector connector;
    private NioSocketAcceptor acceptor;
    private volatile IoSession connection;

    JftnTestPeer(ServerRole role, boolean requestNr, ExchangeFiles files) {
        super(role, requestNr, files);
    }

    @Override
    public void connect(String host, int port) throws Exception {
        connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(5000);
        configure(connector);
        var future = connector.connect(new InetSocketAddress(host, port));
        assertTrue(future.await(10, TimeUnit.SECONDS), "MINA2 connection timeout");
        if (!future.isConnected()) {
            throw new IllegalStateException("MINA2 connection failed", future.getException());
        }
        connection = future.getSession();
    }

    @Override
    public int listen() throws Exception {
        acceptor = new NioSocketAcceptor();
        configure(acceptor);
        acceptor.bind(new InetSocketAddress("127.0.0.1", 0));
        return acceptor.getLocalAddress().getPort();
    }

    private void configure(IoService service) {
        service.getFilterChain().addLast("crypt", new TrafficCrypterCodecFilter());
        service.getFilterChain()
               .addLast("codec", new ProtocolCodecFilter(new BinkDataCodecFactory()));
        service.getFilterChain().addLast("trace", new IoFilterAdapter() {
            @Override
            public void messageReceived(NextFilter next, IoSession io, Object message)
                    throws Exception {
                trace.incoming(message);
                super.messageReceived(next, io, message);
            }

            @Override
            public void filterWrite(NextFilter next, IoSession io, WriteRequest request)
                    throws Exception {
                trace.outgoing(request.getMessage());
                super.filterWrite(next, io, request);
            }
        });
        service.setHandler(new BinkSessionHandler(template, bus) {
            @Override
            public void sessionOpened(IoSession io) throws Exception {
                session = template;
                session.getSendFiles().add(files.outgoingFromJftn());
                connection = io;
                super.sessionOpened(io);
                accepted.countDown();
            }
        });
    }

    @Override
    protected boolean awaitClosed() throws InterruptedException {
        return connection.getCloseFuture().await(30, TimeUnit.SECONDS);
    }

    @Override
    protected void closeTransport() {
        try {
            if (connection != null) {
                connection.closeNow().awaitUninterruptibly();
            }
        } finally {
            try {
                if (connector != null) {
                    connector.dispose();
                }
            } finally {
                if (acceptor != null) {
                    acceptor.unbind();
                    acceptor.dispose();
                }
            }
        }
    }
}
