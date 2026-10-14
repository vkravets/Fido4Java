package org.fidonet.binkp.common;

import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.util.ArrayList;
import java.util.Collections;

/** Common session configuration for unit and loopback TCP tests. */
public final class SessionTestFixtures {
    private SessionTestFixtures() {
    }

    public static SessionContext context() {
        StationConfig station = new StationConfig("station", "sysop", "city", "BINKP", "2:500/1");
        return new SessionContext(station, new LinksInfo(new ArrayList<>()));
    }

    public static SessionContext loopbackContext(String own,
                                                 String peer,
                                                 String password,
                                                 int port) {
        StationConfig station = new StationConfig("Test", "Sysop", "localhost", "BINKP", own);
        station.setPartialDirectory(null);
        Link link = new Link(new FTNAddr(peer),
                             Collections.singletonList(new FTNAddr(own)),
                             password,
                             "127.0.0.1",
                             port);
        return new SessionContext(station,
                                  new LinksInfo(new ArrayList<>(Collections.singletonList(link))));
    }
}
