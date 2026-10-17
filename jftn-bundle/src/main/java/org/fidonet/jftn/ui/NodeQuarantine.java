package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.QuarantineItem;
import org.fidonet.jftn.rpc.QuarantinePage;
import org.fidonet.jftn.tosser.PacketQuarantine;
import org.fidonet.jftn.tosser.Tosser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps packet recovery operations to RPC without accepting filesystem paths. */
final class NodeQuarantine {
    private NodeQuarantine() {
    }

    static QuarantinePage current(Tosser tosser, JFtnConfig config, String after, int limit)
            throws IOException {
        PacketQuarantine.Page page = tosser.quarantine(after, limit);
        List<QuarantineItem> items = new ArrayList<>();
        for (PacketQuarantine.Item item : page.items) {
            items.add(new QuarantineItem(item.id,
                                         item.name,
                                         item.bytes,
                                         item.modified,
                                         item.checksum,
                                         item.peer,
                                         item.reason,
                                         item.attempts,
                                         item.lastAttempt));
        }
        List<String> peers = new ArrayList<>();
        config.getConfiguredLinks().forEach(link -> peers.add(link.getAddr().toString()));
        Collections.sort(peers);
        return new QuarantinePage(items, page.nextCursor, page.hasMore, peers);
    }
}
