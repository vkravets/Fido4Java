package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.db.robots.RobotRules;
import org.fidonet.db.robots.RobotService;
import org.fidonet.types.FTNAddr;

/** Explicit source selection; missing/disabled database rules never fall back to file credentials. */
public final class RobotAccess {
    private final JFtnConfig config;
    private final RobotRules rules;
    private final String source;

    public RobotAccess(JFtnConfig config, DatabaseManager database) {
        this.config = config;
        source = source(config);
        switch (source) {
            case "files" :
                rules = new FileRobotRules(config);
                break;
            case "database" :
                rules = database.getRobotRules();
                break;
            default :
                throw new IllegalArgumentException("RobotAccessSource must be files or database");
        }
    }

    public static String source(JFtnConfig config) {
        String source = config.getValue("RobotAccessSource", "database").trim();
        if (!source.equals("files") && !source.equals("database")) {
            throw new IllegalArgumentException("RobotAccessSource must be files or database");
        }
        return source;
    }

    public String getSource() {
        return source;
    }

    public RobotAccessRule find(String service, FTNAddr peer) {
        if (peer == null || config.getLink(peer) == null) {
            return null;
        }
        return rules.find(RobotService.valueOf(service), peer);
    }

    public RobotAccessRule authorize(String service, FTNAddr peer, String password) {
        RobotAccessRule rule = find(service, peer);
        return rule != null && rule.verifyPassword(password) ? rule : null;
    }
}
