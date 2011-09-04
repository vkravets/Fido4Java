package org.fidonet.protocol.binkp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.List;
import org.apache.log4j.Logger;
import org.fidonet.config.Config;
import org.fidonet.config.JFtnConfig;
import org.fidonet.protocol.binkp.SessionResult;
import org.fidonet.protocol.binkp.Session;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

/**
 *
 * @author toch
 * Implementation of binkd daemon.
 * 
 */
public class BinkClient implements Runnable {
    
    private Logger logger = Logger.getLogger(BinkClient.class);

    boolean active = false;
    Config config = null;
    OutBound outb;
    
    public BinkClient(String conf)
    {
        config = new Config();
        config.ReadConfig(conf);
        outb = new OutBound(config.getOutbound());
        System.out.println("JBink start.");
    }

    @Override
    public void run() {
        active = true;
        while(active)
        {
            FTNAddr f = outb.getPoll();
            if(f != null)
            {
                Link l = config.getLink(f);
                poll(l);
            }
            else
            {
                System.out.println("Nothing to poll.");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
        
    public void stop()
    {
        active = false;
    }
    
    public void poll(Link link) {
        outb.setBusy(link.getAddr());
        Socket clientsock = null;
        try {
            clientsock = new Socket("bbs.agooga.ru", 24554);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread sess = new Thread(new Session(clientsock, link, config));
        sess.start();
        try {
            sess.join(); //TEMPORY
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

}
