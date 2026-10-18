package org.fidonet.jftn.script;

import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.util.Set;

/** Commands are external policy; delivery and subscription storage belong to the provider. */
public interface FileEchoService {
    Set<String> areas() throws IOException;

    Set<String> subscriptions(FTNAddr peer) throws IOException;

    void subscribe(FTNAddr peer, String area, boolean enabled) throws IOException;
}
