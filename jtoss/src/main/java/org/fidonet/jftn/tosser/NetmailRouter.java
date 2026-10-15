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
    private final Link fallback;
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
        fallback = defaultRoute == null ? null : requireLink(defaultRoute.trim());
        transitFrom = masks("TransitFrom");
        transitTo = masks("TransitTo");
        rules();
    }

    private void readConfiguredRules() {
        List<String> values = config.getValuesAsList("Route");
        if (values == null) {
            return;
        }
        for (String value : values) {
            String[] fields = value.split(",", -1);
            if (fields.length != 2) {
                throw new IllegalArgumentException("Expected Route pattern,next-hop");
            }
            int index = configured.size();
            configured.add(new NetmailRoute("config:"
                    + index, index, fields[0].trim(), fields[1].trim(), true));
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
        if (rule.isEnabled()) {
            requireLink(rule.getNextHop());
        }
    }

    private Link requireLink(String address) {
        Link link = config.getLink(new FTNAddr(address));
        if (link == null || config.getLocalAddresses().contains(link.getAddr())) {
            throw new IllegalArgumentException("Next hop must be a configured remote link: "
                                               + address);
        }
        return link;
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
        Link direct = config.getLink(destination);
        String flags = " " + configFlags(message) + " ";
        if (flags.contains(" DIR ") || flags.contains(" CRA ")
                || (message.getAttributes() & 2) != 0) {
            return decision(direct == null
                    ? NetmailDecision.Reason.DIRECT_UNAVAILABLE
                    : NetmailDecision.Reason.DIRECT, direct, null);
        }
        for (NetmailRoute rule : rules()) {
            if (rule.isEnabled() && new AddressPattern(rule.getPattern()).matches(destination)) {
                return decision(NetmailDecision.Reason.RULE,
                                requireLink(rule.getNextHop()),
                                rule.getId());
            }
        }
        if (direct != null) {
            return decision(NetmailDecision.Reason.DIRECT, direct, null);
        }
        return decision(fallback == null
                ? NetmailDecision.Reason.NO_ROUTE
                : NetmailDecision.Reason.DEFAULT, fallback, null);
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
        FTNAddr nextHop = result.getNextHop().getAddr();
        if (nextHop.equals(source)) {
            return decision(NetmailDecision.Reason.RETURN_TO_SOURCE, null, result.getRuleId());
        }
        if (!permitted(transitTo, nextHop)) {
            return decision(NetmailDecision.Reason.TARGET_DENIED, null, result.getRuleId());
        }
        for (String via : message.getBody().getViaLines()) {
            String[] fields = via.trim().split("\\s+");
            if (fields.length > 1 && config.getLocalAddresses().contains(new FTNAddr(fields[1]))) {
                return decision(NetmailDecision.Reason.VIA_LOOP, null, result.getRuleId());
            }
        }
        return result;
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
