package org.fidonet.protocol.binkp;

import java.io.IOException;
import java.net.*;

import org.apache.log4j.Logger;
import org.fidonet.config.JFtnConfig;
import org.fidonet.protocol.binkp.BSO.OutBound;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author toch
 * Implementation of binkd daemon.
 * 
 */
public class BinkClient implements Runnable {
    
    private Logger logger = Logger.getLogger(BinkClient.class);

    private boolean active;
    private JFtnConfig config;
    private OutBound outb;
    private Map<String, Link> links;

    public BinkClient(JFtnConfig config)
    {
        this.outb = new OutBound(config.getOutbound());
        this.config = config;
        setActive(true);
    }

    private Map<String, Link> getLinks(List<String> links) {
        Map<String, Link> result = new HashMap<String, Link>();
        for (String linkStr : links) {
            Link link = new Link(linkStr);
            result.put(link.getAddr().toString(), link);
        }
        return result;
    }

    private Link getLink(FTNAddr addr) {
        return links.get(addr.toString());
    }

    @Override
    public void run() {
        logger.info("JBink started.");
        links = getLinks(config.getValuesAsList("link"));
        while(isActive())
        {
            FTNAddr f = outb.getPoll();
            if(f != null)
            {
                Link l = getLink(f);
                pull(l);
            }
            else
            {
                logger.warn("Nothing to pull.");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        logger.info("JBink stops.");
    }
        
    public void stop()
    {
        setActive(false);
    }
    
    public void pull(Link link) {

        if(!outb.setBusy(link.getAddr()))
        {
            System.out.println("Error while setting busy " + link.getAddr().toString());
            return;
        }

        Socket clientsock = new Socket();
        try {
            clientsock.setSoTimeout(10000);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try {
            clientsock.connect(new InetSocketAddress("bbs.agooga.ru", 24554));
        } catch (IOException e) {
            e.printStackTrace();
            try {
                clientsock.close();
                outb.setUnBusy(link.getAddr());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        if(!clientsock.isConnected()) return;
        Session s = new Session(clientsock, link, config);
        int x = s.run();
        System.out.println("Session end with code " + x);
        if(!outb.setUnBusy(link.getAddr()))
        {
            System.out.println("Error while set unbusy " + link.getAddr().toString());
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
