package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.NetmailRoute;
import org.fidonet.jftn.rpc.NetmailRouteInfo;
import org.fidonet.jftn.rpc.NetmailRoutePreview;
import org.fidonet.jftn.rpc.NetmailRoutesConfiguration;
import org.fidonet.jftn.rpc.NetmailRoutingPolicy;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.tosser.NetmailDecision;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Maps routing domain decisions to administration DTOs; never polls or submits a message. */
final class NodeNetmailRouting {
    private NodeNetmailRouting() {
    }

    static NetmailRoutesConfiguration current(JFtnConfig config, Tosser tosser) {
        List<NetmailRouteInfo> rules = new ArrayList<>();
        for (NetmailRoute rule : tosser.getNetmailRouter().rules()) {
            rules.add(new NetmailRouteInfo(rule.getId(),
                                           rule.getPriority(),
                                           rule.getPattern(),
                                           rule.getNextHop(),
                                           rule.isEnabled()));
        }
        List<String> peers = new ArrayList<>();
        config.getConfiguredLinks().forEach(link -> peers.add(link.getAddr().toString()));
        return new NetmailRoutesConfiguration(new NetmailRoutingPolicy(config.getValue("NetmailRoutes",
                                                                                       "config")
                                                                             .toLowerCase(Locale.ROOT),
                                                                       defaultHop(config),
                                                                       Boolean.parseBoolean(config.getValue("AllowTransit",
                                                                                                            "false")),
                                                                       values(config,
                                                                              "TransitFrom"),
                                                                       values(config, "TransitTo")),
                                              rules,
                                              peers);
    }

    private static List<String> values(JFtnConfig config, String key) {
        List<String> values = config.getValuesAsList(key);
        return values == null ? List.of() : values;
    }

    private static String defaultHop(JFtnConfig config) {
        String value = config.getValue("DefaultRoute", "");
        return value.isEmpty() ? "" : new FTNAddr(value).toString();
    }

    static void requireManaged(JFtnConfig config) {
        if (!"database".equalsIgnoreCase(config.getValue("NetmailRoutes", "config"))) {
            throw new UnsupportedOperationException("Routing rules are managed in the configuration file");
        }
    }

    static void save(JFtnConfig config, Tosser tosser, NetmailRouteInfo rule) {
        requireManaged(config);
        if (rule == null || rule.getPattern() == null || rule.getPattern().length() > 80
                || !rule.isSetPriority() || !rule.isSetEnabled()) {
            throw new IllegalArgumentException("A complete routing rule is required");
        }
        MailDraftValidator.id(rule.getId());
        String next = NetmailRoute.specialTarget(rule.getNextHop())
                ? rule.getNextHop().toLowerCase(Locale.ROOT)
                : AccountDirectory.address(rule.getNextHop(), false);
        if (!NetmailRoute.specialTarget(next) && (config.getLink(new FTNAddr(next)) == null
                || config.getLocalAddresses().contains(new FTNAddr(next)))) {
            throw new IllegalArgumentException("Next hop must be a configured remote link");
        }
        tosser.getNetmailRouter()
              .saveRule(new NetmailRoute(rule.getId(),
                                         rule.getPriority(),
                                         rule.getPattern(),
                                         next,
                                         rule.isEnabled()));
    }

    static NetmailRoutePreview preview(JFtnConfig config,
                                       Tosser tosser,
                                       String target,
                                       String sender,
                                       boolean direct)
            throws Exception {
        FTNAddr destination = new FTNAddr(AccountDirectory.address(target, false));
        FTNAddr source = sender == null || sender.isEmpty()
                ? null
                : new FTNAddr(AccountDirectory.address(sender, false));
        if (source != null && config.getLink(source) == null) {
            throw new IllegalArgumentException("Transit source must be a configured link");
        }
        Message message = new Message("Preview",
                                      "Preview",
                                      config.getLocalAddresses().iterator().next(),
                                      destination,
                                      "Routing preview",
                                      "",
                                      new Date());
        message.setAttributes(direct ? 2 : 1);
        NetmailDecision result = tosser.previewNetmail(message, source);
        return new NetmailRoutePreview(result.getReason().name(),
                                       result.getNextHop() == null
                                               ? ""
                                               : result.getNextHop().getAddr().toString(),
                                       result.getRuleId() == null ? "" : result.getRuleId(),
                                       result.isPermitted());
    }
}
