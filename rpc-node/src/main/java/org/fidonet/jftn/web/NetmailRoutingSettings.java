package org.fidonet.jftn.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.NetmailRoutingPolicy;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.tosser.NetmailRouter;
import org.fidonet.types.FTNAddr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Routing policy publication uses the same validated live configuration transaction as station edits. */
final class NetmailRoutingSettings {
    private NetmailRoutingSettings() {
    }

    static void save(NodeConfigurationService store, NetmailRoutingPolicy policy) throws Exception {
        if (policy == null || !policy.isSetAllowTransit() || policy.getMode() == null
                || !List.of("config", "database").contains(policy.getMode())
                || policy.getDefaultHop() == null) {
            throw new IllegalArgumentException("Routing mode, default hop and transit policy are required");
        }
        JFtnConfig config = store.configuration();
        String next = policy.getDefaultHop().isEmpty()
                ? ""
                : AccountDirectory.address(policy.getDefaultHop(), false);
        if (!next.isEmpty() && (config.getLink(new FTNAddr(next)) == null
                || config.getLocalAddresses().contains(new FTNAddr(next)))) {
            throw new IllegalArgumentException("Default route must be a configured remote link");
        }
        validateMasks(policy.getTransitFrom());
        validateMasks(policy.getTransitTo());
        Map<String, List<String>> changes = new LinkedHashMap<>();
        changes.put("netmailroutes", List.of(policy.getMode()));
        changes.put("defaultroute", next.isEmpty() ? List.of() : List.of(next));
        changes.put("allowtransit", List.of(Boolean.toString(policy.isAllowTransit())));
        changes.put("transitfrom", policy.getTransitFrom());
        changes.put("transitto", policy.getTransitTo());
        store.replaceOptions(changes);
    }

    private static void validateMasks(List<String> values) {
        if (values == null || values.size() > 64) {
            throw new IllegalArgumentException("At most 64 transit masks are allowed");
        }
        for (String mask : values) {
            if (mask == null || mask.length() > 80) {
                throw new IllegalArgumentException("Transit masks must fit 80 characters");
            }
            NetmailRouter.validateMask(mask);
        }
    }
}
