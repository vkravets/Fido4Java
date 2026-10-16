package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.OrmManager;
import org.fidonet.db.plugin.DatabasePlugin;
import org.fidonet.db.robots.DatabaseRobotRules;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.jftn.script.RobotAccess;

/** Offline administration that does not start listeners, plugins or the first-run web wizard. */
final class RobotAccessCommand {
    private RobotAccessCommand() {
    }

    static boolean handle(String[] args) throws Exception {
        if (args.length == 0 || !(args[0].equals("--check-robot-access")
                || args[0].equals("--import-robot-access"))) {
            return false;
        }
        if (args.length > 2) {
            throw new IllegalArgumentException("Expected command [configuration-file]");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(args.length == 2 ? args[1] : "jftn.conf");
        RobotAccess.source(config);
        FileRobotRules files = new FileRobotRules(config);
        if (args[0].equals("--check-robot-access")) {
            System.out.println("Validated robot access rules: " + files.all().size());
            return true;
        }
        OrmManager orm = new OrmManager(DatabasePlugin.DEFAULT_DATABASE_URL);
        try {
            orm.connect();
            new DatabaseRobotRules(orm).importIntoEmpty(files.all());
            System.out.println("Imported robot access rules: " + files.all().size());
            System.out.println("Select RobotAccessSource=database and restart to use them. Configuration files were not changed.");
        } finally {
            orm.disconnect();
        }
        return true;
    }
}
