package binkp;

import types.Link;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 16.02.11
 * Time: 12:52
 */
public class binkp {

    public binkp() {

    }

    public SessionResult Poll(Link link) {
        Socket clientsock = null;
        try {
            clientsock = new Socket("bbs.agooga.ru", 24554);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Session session = new Session(clientsock, link);

        session.run();

        return session.getResult();

    }

}
