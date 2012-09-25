package org.fidonet.binkp;

import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.fidonet.binkp.codec.BinkDataDecoder;
import org.fidonet.binkp.codec.BinkDataEncoder;
import org.fidonet.binkp.handler.BinkSessionHandler;
import org.fidonet.binkp.io.BinkFrame;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:02 PM
 */
public class Server extends Connector{

    private NioSocketAcceptor acceptor;
    private int port = Connector.BINK_PORT;

    public Server() { }

    public Server(int port) {
        this.port = port;
    }

    @Override
    public void run(final SessionContext context) throws Exception {
        acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new BinkDataEncoder<BinkFrame>(),new BinkDataDecoder()));
        acceptor.setHandler(new BinkSessionHandler());
        acceptor.addListener(new IoServiceListener() {
            @Override
            public void serviceActivated(IoService ioService) throws Exception {
                // server is started
            }

            @Override
            public void serviceIdle(IoService ioService, IdleStatus idleStatus) throws Exception {
                // server waiting the connection
            }

            @Override
            public void serviceDeactivated(IoService ioService) throws Exception {
                // server is closed
            }

            @Override
            public void sessionCreated(IoSession session) throws Exception {
                // got new connection to server
                session.setAttribute(SessionContext.SESSION_CONTEXT_KEY, new SessionContext(context));
                //session.getRemoteAddress()
            }

            @Override
            public void sessionDestroyed(IoSession session) throws Exception {
                SessionContext context = (SessionContext)session.getAttribute(SessionContext.SESSION_CONTEXT_KEY);
                if (context.getState().equals(SessionState.STATE_ERR)) {
                    // TODO log or out error message
                    context.getLastErrorMessage();
                }
            }
        });
        acceptor.bind();
    }

    @Override
    public void stop(SessionContext context) {
        acceptor.dispose();
    }
}
