package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.jftn.rpc.EchoAccessConfiguration;
import org.fidonet.jftn.rpc.EchoSubscriptionInfo;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.tosser.EchoRouter;
import org.fidonet.types.Link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Reads current echo permissions under the mail service operation lock. */
final class NodeEchoAccess {
    private NodeEchoAccess() {
    }

    static void requireManaged(JFtnConfig config) throws UiError {
        if (!EchoRouter.databaseSubscriptions(config)) {
            throw new UiError("READ_ONLY",
                              "Echo permissions are managed through configuration files.");
        }
    }

    static EchoAccessConfiguration current(JFtnConfig config, DatabaseManager base) {
        List<String> peers = new ArrayList<>();
        List<EchoSubscriptionInfo> subscriptions = new ArrayList<>();
        for (Link link : config.getConfiguredLinks()) {
            String peer = link.getAddr().toString();
            peers.add(peer);
            base.getEchoAccess(link)
                .forEach((area,
                          access) -> subscriptions.add(new EchoSubscriptionInfo(peer,
                                                                                area,
                                                                                access.name())));
        }
        Collections.sort(peers);
        subscriptions.sort(Comparator.comparing(EchoSubscriptionInfo::getArea)
                                     .thenComparing(EchoSubscriptionInfo::getPeer));
        List<String> areas = new ArrayList<>(base.getAreas());
        Collections.sort(areas);
        return new EchoAccessConfiguration(EchoRouter.databaseSubscriptions(config)
                ? "database"
                : "config", peers, areas, subscriptions);
    }
}
