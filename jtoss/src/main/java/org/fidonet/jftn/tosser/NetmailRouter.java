package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.NetmailRoute;
import org.fidonet.echobase.NetmailRoutingStore;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Explicit routing policy shared by submission, transit and operator previews. */
public final class NetmailRouter {
    private final JFtnConfig config;
    private final NetmailRoutingStore store;
    private final List<NetmailRoute> configured = new ArrayList<>();
    private final boolean databaseRules;
    private final String fallback;
    private final List<AddressPattern> transitFrom;
    private final List<AddressPattern> transitTo;

    public NetmailRouter(JFtnConfig config) {
        this(config, null);
    }

    public NetmailRouter(JFtnConfig config, NetmailRoutingStore store) {
        this.config = config;
        this.store = store;
        String mode = config.getValue("NetmailRoutes", "config");
        if (!mode.equalsIgnoreCase("config") && !mode.equalsIgnoreCase("database")) {
            throw new IllegalArgumentException("NetmailRoutes must be config or database");
        }
        databaseRules = mode.equalsIgnoreCase("database");
        if (databaseRules && store == null) {
            throw new IllegalArgumentException("Database routing requires a NetmailRoutingStore");
        }
        if (!databaseRules) {
            readConfiguredRules();
        }
        String defaultRoute = config.getValue("DefaultRoute");
        fallback = defaultRoute == null ? null : defaultRoute.trim();
        if (fallback != null) {
            validateTarget(fallback);
        }
        transitFrom = masks("TransitFrom");
        transitTo = masks("TransitTo");
        rules();
    }

    private void readConfiguredRules() {
        readConfiguredRules("Route", "config:");
        readConfiguredRules("RouteMail", "config-mail:");
        readConfiguredRules("RouteFile", "file-config:");
    }

    private void readConfiguredRules(String key, String prefix) {
        List<String> values = config.getValuesAsList(key);
        if (values == null) {
            return;
        }
        for (String value : values) {
            String[] fields = value.split(",", -1);
            if (fields.length != 2) {
                throw new IllegalArgumentException("Expected Route pattern,next-hop");
            }
            int index = configured.size();
            configured.add(new NetmailRoute(prefix + index,
                                            index,
                                            fields[0].trim(),
                                            fields[1].trim(),
                                            true));
        }
    }

    public List<NetmailRoute> rules() {
        List<NetmailRoute> result = new ArrayList<>(databaseRules
                ? store.getNetmailRoutes()
                : configured);
        result.sort(Comparator.comparingInt(NetmailRoute::getPriority)
                              .thenComparing(NetmailRoute::getId));
        for (NetmailRoute rule : result) {
            validate(rule);
        }
        return result;
    }

    public void saveRule(NetmailRoute rule) {
        requireDatabaseRules();
        validate(rule);
        store.saveNetmailRoute(rule);
    }

    public static void validateMask(String value) {
        new AddressPattern(value);
    }

    public void removeRule(String id) {
        requireDatabaseRules();
        store.removeNetmailRoute(id);
    }

    private void requireDatabaseRules() {
        if (!databaseRules) {
            throw new IllegalStateException("Enable NetmailRoutes=database to administer rules");
        }
    }

    private void validate(NetmailRoute rule) {
        new AddressPattern(rule.getPattern());
        if (rule.isEnabled() && !NetmailRoute.specialTarget(rule.getNextHop())) {
            validateTarget(rule.getNextHop());
        }
    }

    private Link requireLink(String address) {
        Link link = OutgoingLinks.find(config, new FTNAddr(address));
        if (link == null || config.getLocalAddresses().contains(link.getAddr())) {
            throw new IllegalArgumentException("Next hop must be a configured remote link: "
                                               + address);
        }
        return link;
    }

    private void validateTarget(String target) {
        FTNAddr address = new FTNAddr(target);
        if (!address.isValid() || config.getLocalAddresses().contains(address)) {
            throw new IllegalArgumentException("Next hop must be a remote FTN address");
        }
        if (!OutgoingLinks.virtualEnabled(config)) {
            requireLink(target);
        }
    }

    public Link route(Message message) {
        return explain(message).getNextHop();
    }

    public NetmailDecision explain(Message message) {
        FTNAddr destination = message.getTAddr();
        if (destination == null || !destination.isValid()) {
            return decision(NetmailDecision.Reason.INVALID_DESTINATION, null, null);
        }
        if (config.getLocalAddresses().contains(destination)) {
            return decision(NetmailDecision.Reason.LOCAL, null, null);
        }
        Link direct = OutgoingLinks.find(config, destination);
        if (NetmailPriority.of(message) != NetmailPriority.NORMAL
                || (message.getAttributes() & 0x0800) != 0) {
            return decision(direct == null
                    ? NetmailDecision.Reason.DIRECT_UNAVAILABLE
                    : NetmailDecision.Reason.DIRECT, direct, null);
        }
        for (NetmailRoute rule : rules()) {
            if (rule.isEnabled() && rule.isFileOnly() == ((message.getAttributes() & 16) != 0)
                    && new AddressPattern(rule.getPattern()).matches(destination)) {
                return matchedRule(rule, destination);
            }
        }
        if ((message.getAttributes() & 16) != 0) {
            return decision(NetmailDecision.Reason.NO_ROUTE, null, null);
        }
        if (direct != null) {
            return decision(NetmailDecision.Reason.DIRECT, direct, null);
        }
        Link fallbackLink = fallback == null
                ? null
                : OutgoingLinks.find(config, new FTNAddr(fallback));
        return decision(fallbackLink == null
                ? NetmailDecision.Reason.NO_ROUTE
                : NetmailDecision.Reason.DEFAULT, fallbackLink, null);
    }

    public NetmailDecision explainTransit(Message message, FTNAddr source) {
        NetmailDecision result = explain(message);
        if (result.getReason() == NetmailDecision.Reason.LOCAL) {
            return result;
        }
        if (!Boolean.parseBoolean(config.getValue("AllowTransit", "false"))) {
            return decision(NetmailDecision.Reason.TRANSIT_DISABLED, null, null);
        }
        if (source == null || config.getLink(source) == null || !permitted(transitFrom, source)) {
            return decision(NetmailDecision.Reason.SOURCE_DENIED, null, null);
        }
        if (!result.isPermitted()) {
            return result;
        }
        if (hasLocalVia(message)) {
            return decision(NetmailDecision.Reason.VIA_LOOP, null, result.getRuleId());
        }
        if (result.getReason() == NetmailDecision.Reason.NO_PACK) {
            return result;
        }
        FTNAddr nextHop = result.getNextHop().getAddr();
        if (nextHop.equals(source)) {
            return decision(NetmailDecision.Reason.RETURN_TO_SOURCE, null, result.getRuleId());
        }
        if (!permitted(transitTo, nextHop)) {
            return decision(NetmailDecision.Reason.TARGET_DENIED, null, result.getRuleId());
        }
        return result;
    }

    private boolean hasLocalVia(Message message) {
        for (String via : message.getBody().getViaLines()) {
            String[] fields = via.trim().split("\\s+");
            if (fields.length > 1 && config.getLocalAddresses().contains(new FTNAddr(fields[1]))) {
                return true;
            }
        }
        return false;
    }

    private NetmailDecision matchedRule(NetmailRoute rule, FTNAddr destination) {
        String target = rule.getNextHop();
        if (target.equals("nopack")) {
            return decision(NetmailDecision.Reason.NO_PACK, null, rule.getId());
        }
        if (!NetmailRoute.specialTarget(target)) {
            Link link = OutgoingLinks.find(config, new FTNAddr(target));
            return decision(link == null
                    ? NetmailDecision.Reason.DIRECT_UNAVAILABLE
                    : NetmailDecision.Reason.RULE, link, rule.getId());
        }
        int node = destination.getNode();
        int point = destination.getPnt();
        if (target.equals("host")) {
            node = 0;
            point = 0;
        } else if (target.equals("boss")) {
            point = 0;
        } else if (target.equals("hub")) {
            node -= node % 100;
            point = 0;
        }
        FTNAddr address = new FTNAddr(destination.getZone(), destination.getNet(), node, point);
        Link link = OutgoingLinks.find(config, address);
        return decision(link == null
                ? NetmailDecision.Reason.DIRECT_UNAVAILABLE
                : NetmailDecision.Reason.RULE, link, rule.getId());
    }

    private List<AddressPattern> masks(String key) {
        List<AddressPattern> result = new ArrayList<>();
        List<String> values = config.getValuesAsList(key);
        if (values != null) {
            for (String value : values) {
                result.add(new AddressPattern(value.trim()));
            }
        }
        return result;
    }

    private boolean permitted(List<AddressPattern> masks, FTNAddr address) {
        return masks.isEmpty()
                || address != null && masks.stream().anyMatch(mask -> mask.matches(address));
    }

    private NetmailDecision decision(NetmailDecision.Reason reason, Link link, String rule) {
        return new NetmailDecision(reason, link, rule);
    }

    static String configFlags(Message message) {
        String flags = message.getBody().getKludges().get("FLAGS");
        return flags == null ? "" : flags.trim();
    }
}
