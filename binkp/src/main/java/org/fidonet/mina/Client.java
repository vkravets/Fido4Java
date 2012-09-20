package org.fidonet.mina;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.fidonet.mina.codec.BinkDataDecoder;
import org.fidonet.mina.codec.BinkDataEncoder;
import org.fidonet.mina.commands.SessionContext;
import org.fidonet.mina.io.BinkFrame;
import org.fidonet.types.Link;

import java.net.InetSocketAddress;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Client {
    private long connectionTimeout = 30*1000L;
    private static final int BINK_PORT = 24554;

    private NioSocketConnector connector;
    private IoSession session;
    private Link link;

    public Client(Link link) {
        this.link = link;
    }

    public Client(Link link, long connectionTimeout) {
        this(link);
        this.connectionTimeout = connectionTimeout;
    }

    public void connect(SessionContext sessionContext) throws Exception {
        connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(connectionTimeout);
        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new BinkDataEncoder<BinkFrame>(),new BinkDataDecoder()));
        connector.setHandler(new BinkClientSessionHandler(sessionContext));
        String hostname = link.getHostAddress();
        int port = link.getPort() != 0 ? link.getPort(): BINK_PORT;
        ConnectFuture connection = connector.connect(new InetSocketAddress(hostname, port));
        connection.awaitUninterruptibly();
        if (connection.isConnected()) {
            session = connection.getSession();
        } else {
            System.out.println("Cannot connect to target host");
        }

    }

    public void disconnect() throws Exception {
        if (session != null) {
            session.getCloseFuture().awaitUninterruptibly();
        }
        if (connector != null) {
            connector.dispose();
        }
    }
}
