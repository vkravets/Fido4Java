package org.fidonet.jftn;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.poll.OutboundTransportFactory;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds the transport context without optional RPC or browser dependencies. */
public final class NodeSessions {
    private NodeSessions() {
    }
    public static SessionContext create(JFtnConfig config) {
        new OutboundTransportFactory(config).validate();
        List<Link> links = new ArrayList<>();
        Set<String> addresses = new LinkedHashSet<>();
        for (String value : config.getValuesAsList("Link")) {
            Link link = new Link(value);
            links.add(link);
            for (FTNAddr address : link.getMyaddr()) {
                addresses.add(address.toString());
            }
        }
        StationConfig station = new StationConfig(config.getValue("System", "JFtn"),
                                                  config.getValue("SysOp"),
                                                  config.getValue("Location", ""),
                                                  config.getValue("NDL", "BINKP"),
                                                  String.join(" ", addresses));
        return new SessionContext(station, new LinksInfo(links));
    }
}
