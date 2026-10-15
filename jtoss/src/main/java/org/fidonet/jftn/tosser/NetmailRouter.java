package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.util.List;
import java.util.regex.Pattern;

/** Explicit local routing policy; unknown destinations are never silently discarded. */
final class NetmailRouter {
    private final JFtnConfig config;

    NetmailRouter(JFtnConfig config) {
        this.config = config;
    }

    Link route(Message message) {
        String flags = " " + configFlags(message) + " ";
        boolean direct = flags.contains(" DIR ") || flags.contains(" CRA ")
                || (message.getAttributes() & 2) != 0;
        Link destination = config.getLink(message.getTAddr());
        if (direct) {
            return destination;
        }
        List<String> routes = config.getValuesAsList("Route");
        if (routes != null) {
            for (String value : routes) {
                String[] fields = value.split(",", 2);
                if (fields.length != 2) {
                    throw new IllegalArgumentException("Expected Route pattern,next-hop");
                }
                String pattern = fields[0].trim();
                String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
                String address = message.getTAddr().toString();
                if (Pattern.matches(regex, address)
                        || Pattern.matches(regex, address.replaceFirst("\\.0$", ""))) {
                    return config.getLink(new FTNAddr(fields[1].trim()));
                }
            }
        }
        if (destination != null) {
            return destination;
        }
        String fallback = config.getValue("DefaultRoute");
        return fallback == null ? null : config.getLink(new FTNAddr(fallback));
    }

    static String configFlags(Message message) {
        String flags = message.getBody().getKludges().get("FLAGS");
        return flags == null ? "" : flags.trim();
    }
}
