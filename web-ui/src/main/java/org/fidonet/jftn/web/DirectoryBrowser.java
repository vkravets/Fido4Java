package org.fidonet.jftn.web;

import org.fidonet.jftn.rpc.DirectoryNode;
import org.fidonet.jftn.rpc.DirectoryPage;
import org.fidonet.nodelist.DirectoryCatalog;
import org.fidonet.nodelist.DirectoryEntry;
import org.fidonet.nodelist.InternetEndpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bounded, read-only projection of imported lists; never downloads or creates links. */
final class DirectoryBrowser {
    private DirectoryBrowser() {
    }

    static DirectoryPage search(Path path,
                                String query,
                                int offset,
                                int limit,
                                boolean includePoints)
            throws IOException {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Directory page size must be 1..100");
        }
        try (DirectoryCatalog catalog = new DirectoryCatalog(path)) {
            List<DirectoryEntry> entries = catalog.search(query, offset, limit + 1, includePoints);
            List<DirectoryNode> items = new ArrayList<>();
            for (DirectoryEntry entry : entries.subList(0, Math.min(limit, entries.size()))) {
                String kind = entry.getAddress().getPnt() != 0
                        ? "POINT"
                        : entry.getKeyword().isEmpty() ? "NODE" : entry.getKeyword();
                items.add(new DirectoryNode(entry.getAddress().toString(),
                                            entry.getSource(),
                                            kind,
                                            entry.getSystem().replace('_', ' '),
                                            entry.getSysop(),
                                            entry.getLocation(),
                                            entry.getPhone(),
                                            endpoints(entry),
                                            entry.getFlags()));
            }
            int next = offset + items.size();
            return new DirectoryPage(items, next, entries.size() > limit && next <= 1000000);
        }
    }

    private static List<String> endpoints(DirectoryEntry entry) {
        List<String> values = new ArrayList<>();
        try {
            for (InternetEndpoint endpoint : entry.getBinkpEndpoints()) {
                String host = endpoint.getHost();
                values.add((host.contains(":") ? "[" + host + "]" : host)
                           + ":"
                           + endpoint.getPort());
            }
        } catch (IllegalArgumentException invalidEndpoint) {
            // Retain the source record and raw flags even when its endpoint cannot be used.
        }
        return values;
    }
}
