package org.fidonet.config;

import org.fidonet.types.FTNAddr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Explicit senders allowed to create SQL echo areas; never implies trust in other peers. */
public final class EchoAutoCreatePolicy {
    private EchoAutoCreatePolicy() {
    }

    public static Map<String, String> read(JFtnConfig config) {
        Map<String, String> rules = new LinkedHashMap<>();
        List<String> declarations = config.getValuesAsList("AutoCreateEchoFrom");
        if (declarations == null || declarations.isEmpty()) {
            return rules;
        }
        if (RuntimeProfile.read(config) == RuntimeProfile.FULL
                && !config.getValue("EchoSubscriptions", "config").equalsIgnoreCase("database")) {
            throw new IllegalArgumentException("AutoCreateEchoFrom requires EchoSubscriptions=database");
        }
        for (String declaration : declarations) {
            String[] fields = declaration.split(",", -1);
            if (fields.length != 2
                    || !fields[0].trim().matches("[0-9]+:[0-9]+/[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException("Expected AutoCreateEchoFrom address,WRITE or BOTH");
            }
            FTNAddr address = new FTNAddr(fields[0].trim());
            String access = fields[1].trim().toUpperCase(Locale.ROOT);
            if (config.getLink(address) == null || !List.of("WRITE", "BOTH").contains(access)) {
                throw new IllegalArgumentException("AutoCreateEchoFrom requires a configured link and WRITE or BOTH access");
            }
            if (rules.putIfAbsent(address.toString(), access) != null) {
                throw new IllegalArgumentException("Duplicate AutoCreateEchoFrom link");
            }
        }
        return rules;
    }
}
