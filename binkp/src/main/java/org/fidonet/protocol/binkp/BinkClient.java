package org.fidonet.protocol.binkp;

import org.apache.log4j.Logger;
import org.fidonet.config.IConfig;
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

    private boolean active = false;
    private IConfig config;
    private OutBound outb;
    private Map<String, Link> links;

    public BinkClient(IConfig config)
    {
        this.outb = new OutBound(getOutbound());
        this.config = config;
        logger.debug("JBink start.");
    }

    private String getOutbound() {
        return config.getValue("outbound");
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
        active = true;
        links = getLinks(config.getValuesAsList("link"));
        while(active)
        {
            FTNAddr f = outb.getPoll();
            if(f != null)
            {
                Link l = getLink(f);
                poll(l);
            }
            else
            {
                logger.warn("Nothing to poll.");
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
