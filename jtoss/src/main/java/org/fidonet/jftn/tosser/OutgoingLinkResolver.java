package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;

/** Optional directory lookup for outgoing delivery; never registers a trusted incoming link. */
public interface OutgoingLinkResolver {
    Link lookup(JFtnConfig config, FTNAddr address) throws IOException;
}
