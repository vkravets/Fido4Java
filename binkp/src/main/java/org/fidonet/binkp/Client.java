package org.fidonet.binkp;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.fidonet.binkp.codec.BinkDataCodecFactory;
import org.fidonet.binkp.handler.BinkSessionHandler;
import org.fidonet.types.Link;

import java.net.InetSocketAddress;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Client extends Connector {
    private long connectionTimeout = 30*1000L;

    private NioSocketConnector connector;
    private IoSession session;
    private Link link;

    private boolean connected = false;

    public Client(Link link) {
        this.link = link;
    }

    public Client(Link link, long connectionTimeout) {
        this(link);
        this.connectionTimeout = connectionTimeout;
    }

    public void run(SessionContext sessionContext) throws Exception {
        connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(connectionTimeout);
        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new BinkDataCodecFactory()));
        connector.setHandler(new BinkSessionHandler(sessionContext));
        String hostname = link.getHostAddress();
        int port = link.getPort() != 0 ? link.getPort(): Connector.BINK_PORT;
        ConnectFuture connection = connector.connect(new InetSocketAddress(hostname, port));
        connection.awaitUninterruptibly();
        connected = connection.isConnected();
        if (connected) {
            session = connection.getSession();
        } else {
            System.out.println("Cannot connect to target host");
        }

    }

    public void stop(SessionContext context) {
        if (session != null) {
            session.getCloseFuture().awaitUninterruptibly();
        }
        if (connector != null) {
            connector.dispose();
        }
    }

    public boolean isConnect() {
        return connected;
    }
}
