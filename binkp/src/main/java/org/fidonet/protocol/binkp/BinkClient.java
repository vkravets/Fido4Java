package org.fidonet.protocol.binkp;

import org.apache.log4j.Logger;
import org.fidonet.config.JFtnConfig;
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
        outb.setBusy(link.getAddr());
        Socket clientsock = null;
        try {
            clientsock = new Socket("bbs.agooga.ru", 24554);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        Thread sess = new Thread(new Session(clientsock, link, config));
        sess.start();
        try {
            sess.join(); //TEMPORY
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }

    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
