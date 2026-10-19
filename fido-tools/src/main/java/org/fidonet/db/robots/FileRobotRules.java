package org.fidonet.db.robots;

import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Validated snapshot of merged main/conf.d configuration; no RPC or database is required. */
public final class FileRobotRules implements RobotRules {
    private final List<RobotAccessRule> rules;

    public FileRobotRules(JFtnConfig config) {
        if (config.getValue("RobotAccessFormat") != null) {
            rules = validated(config, RobotRuleSnapshot.read(config));
            return;
        }
        if (config.getValuesAsList("RobotAccessRule") != null) {
            throw new IllegalArgumentException("RobotAccessRule requires RobotAccessFormat=1");
        }
        List<RobotAccessRule> loaded = new ArrayList<>();
        for (RobotService service : RobotService.values()) {
            load(config, service, loaded);
        }
        rules = List.copyOf(loaded);
    }

    private static List<RobotAccessRule> validated(JFtnConfig config, List<RobotAccessRule> rules) {
        Set<String> identities = new HashSet<>();
        for (RobotAccessRule rule : rules) {
            if (config.getLink(new FTNAddr(rule.getAddress())) == null
                    || !identities.add(rule.getService().name() + ":" + rule.getAddress())) {
                throw new IllegalArgumentException("RobotAccessRule requires unique configured links per service");
            }
        }
        return List.copyOf(rules);
    }

    private static void load(JFtnConfig config,
                             RobotService service,
                             List<RobotAccessRule> result) {
        List<String> values = config.getValuesAsList(service.name() + "Peer");
        if (values == null) {
            return;
        }
        Set<String> peers = new HashSet<>();
        for (String value : values) {
            String[] fields = value.split("\\|", -1);
            int expected = service == RobotService.AreaFix ? 4 : 3;
            if (fields.length != expected) {
                throw new IllegalArgumentException("Invalid " + service + "Peer field count");
            }
            RobotAccessRule rule = RobotAccessRule.create(service,
                                                          fields[0].trim(),
                                                          fields[1],
                                                          fields[2].trim(),
                                                          expected == 4 ? fields[3].trim() : "",
                                                          true);
            if (config.getLink(new FTNAddr(rule.getAddress())) == null
                    || !peers.add(rule.getAddress())) {
                throw new IllegalArgumentException(service
                                                   + "Peer requires unique configured links");
            }
            result.add(rule);
        }
    }

    @Override
    public List<RobotAccessRule> list(RobotService service) {
        return rules.stream()
                    .filter(rule -> rule.getService() == service)
                    .collect(Collectors.toUnmodifiableList());
    }

    public List<RobotAccessRule> all() {
        return rules;
    }
}
