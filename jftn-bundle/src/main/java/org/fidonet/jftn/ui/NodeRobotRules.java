package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.robots.DatabaseRobotRules;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.db.robots.RobotRules;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.rpc.RobotAccessConfiguration;
import org.fidonet.jftn.rpc.RobotRuleChange;
import org.fidonet.jftn.rpc.RobotRuleInfo;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.script.RobotAccess;
import org.fidonet.types.FTNAddr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Administrative rule operations; authorization belongs to the shared RPC boundary. */
final class NodeRobotRules {
    private final JFtnConfig config;
    private final String source;
    private final DatabaseRobotRules database;
    private final RobotRules rules;

    NodeRobotRules(JFtnConfig config, MailDatabaseFacade base) {
        this.config = config;
        source = RobotAccess.source(config);
        database = base.getRobotRules();
        rules = source.equals("database") ? database : new FileRobotRules(config);
    }

    synchronized RobotAccessConfiguration current() {
        List<RobotRuleInfo> result = new ArrayList<>();
        for (RobotService service : RobotService.values()) {
            for (RobotAccessRule rule : rules.list(service)) {
                result.add(new RobotRuleInfo(service.name(),
                                             rule.getAddress(),
                                             rule.getMasks(),
                                             rule.getAccess(),
                                             rule.isEnabled()));
            }
        }
        result.sort(Comparator.comparing(RobotRuleInfo::getService)
                              .thenComparing(RobotRuleInfo::getAddress));
        List<String> peers = new ArrayList<>();
        config.getConfiguredLinks().forEach(link -> peers.add(link.getAddr().toString()));
        peers.sort(String::compareTo);
        return new RobotAccessConfiguration(source, result, peers);
    }

    private void writable() throws UiError {
        if (!source.equals("database")) {
            throw new UiError("READ_ONLY",
                              "Set RobotAccessSource=database in conf.d/40-mail-scripts.conf and restart");
        }
    }

    private FTNAddr peer(String address) {
        if (address == null || address.length() > 100) {
            throw new IllegalArgumentException("A configured link address is required");
        }
        FTNAddr peer = new FTNAddr(address);
        if (!peer.isValid() || config.getLink(peer) == null) {
            throw new IllegalArgumentException("Robot rule requires a configured link");
        }
        return peer;
    }

    private RobotService service(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Robot service is required");
        }
        return RobotService.valueOf(value);
    }

    synchronized void save(RobotRuleChange change) throws UiError {
        writable();
        if (change == null || change.getRule() == null) {
            throw new IllegalArgumentException("Robot rule is required");
        }
        RobotRuleInfo info = change.getRule();
        RobotService service = service(info.getService());
        FTNAddr peer = peer(info.getAddress());
        if (!info.isSetEnabled()) {
            throw new IllegalArgumentException("Rule enabled state is required");
        }
        RobotAccessRule updated = updatedRule(change, service, peer);
        database.save(updated);
    }

    private RobotAccessRule updatedRule(RobotRuleChange change,
                                        RobotService service,
                                        FTNAddr peer) {
        RobotRuleInfo info = change.getRule();
        String password = change.getPassword();
        if (password != null && !password.isEmpty()) {
            return RobotAccessRule.create(service,
                                          peer.toString(),
                                          password,
                                          info.getMasks(),
                                          info.getAccess(),
                                          info.isEnabled());
        }
        // list includes disabled rules, whose credentials must survive re-enabling.
        RobotAccessRule existing = database.list(service)
                                           .stream()
                                           .filter(rule -> rule.getAddress()
                                                               .equals(peer.toString()))
                                           .findFirst()
                                           .orElseThrow(() -> new IllegalArgumentException("A password is required for a new rule"));
        return existing.withPolicy(info.getMasks(), info.getAccess(), info.isEnabled());
    }

    synchronized void delete(String service, String address) throws UiError {
        writable();
        // Allow removal of stale rules after a link has been removed from configuration.
        if (address == null || address.length() > 100 || !new FTNAddr(address).isValid()) {
            throw new IllegalArgumentException("A valid peer address is required");
        }
        database.delete(service(service), new FTNAddr(address));
    }
}
