package org.fidonet.nodelist;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.BsoOutbound;
import org.fidonet.jftn.tosser.OutgoingLinkResolver;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Read-only directory projection; configured passwords and inbound trust are never inferred. */
public final class DirectoryOutgoingLinks implements OutgoingLinkResolver {
    @Override
    public Link lookup(JFtnConfig config, FTNAddr address) throws IOException {
        Path database = Path.of(config.getValue("NodeDirectoryPath",
                                                "./database/nodelist/catalog"));
        if (!Files.isRegularFile(Path.of(database + ".mv.db"))
                || config.getLocalAddresses().isEmpty()) {
            return null;
        }
        try (DirectoryCatalog catalog = new DirectoryCatalog(database)) {
            for (DirectoryEntry entry : catalog.lookup(address)) {
                Link link = fromEntry(config, entry);
                if (link != null) {
                    return link;
                }
            }
            return null;
        }
    }

    private Link fromEntry(JFtnConfig config, DirectoryEntry entry) {
        if (entry.getKeyword().equalsIgnoreCase("Down")
                || entry.getKeyword().equalsIgnoreCase("Hold")) {
            return null;
        }
        List<InternetEndpoint> endpoints = entry.getBinkpEndpoints();
        if (endpoints.isEmpty() && !BsoOutbound.enabled(config)) {
            return null;
        }
        String host = endpoints.isEmpty() ? "" : endpoints.get(0).getHost();
        int port = endpoints.isEmpty() ? 0 : endpoints.get(0).getPort();
        return new Link(entry.getAddress(),
                        new ArrayList<>(config.getLocalAddresses()),
                        "",
                        host,
                        port);
    }
}
