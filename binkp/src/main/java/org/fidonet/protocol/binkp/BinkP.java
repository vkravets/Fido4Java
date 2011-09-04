package org.fidonet.protocol.binkp;

import org.apache.log4j.Logger;
import org.fidonet.config.IConfig;
import org.fidonet.types.Link;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 16.02.11
 * Time: 12:52
 */
public class BinkP {

    private static Logger logger = Logger.getLogger(BinkP.class);

    public BinkP() {

    }

    public SessionResult pull(Link link, IConfig config) {
        try {
            Socket clientsock = new Socket("bbs.agooga.ru", 24554);
            Session session = new Session(clientsock, link, config);
            session.run();
            return session.getResult();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
