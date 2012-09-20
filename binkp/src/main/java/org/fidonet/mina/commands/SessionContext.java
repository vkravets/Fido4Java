package org.fidonet.mina.commands;

import org.fidonet.binkp.StationConfig;
import org.fidonet.types.Link;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:49 PM
 */
public class SessionContext {
    // current link
    // received files number
    // received size
    // send files number
    // send size
    // queue received files
    // queue send files

    // config client/server

    // last error message
    private String lastErrorMessage;
    private StationConfig stationConfig;
    private Link link;

    public SessionContext(StationConfig config, Link link) {
        this.stationConfig = config;
        this.link = link;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public StationConfig getStationConfig() {
        return stationConfig;
    }

    public Link getLink() {
        return link;
    }
}
