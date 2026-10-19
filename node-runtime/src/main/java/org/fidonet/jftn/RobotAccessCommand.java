package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.OrmManager;
import org.fidonet.db.plugin.DatabasePlugin;
import org.fidonet.db.robots.DatabaseRobotRules;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.db.robots.RobotRuleSnapshot;
import org.fidonet.jftn.script.RobotAccess;

import java.nio.file.Path;

/** Offline administration that does not start listeners, plugins or the first-run web wizard. */
final class RobotAccessCommand {
    private RobotAccessCommand() {
    }

    static boolean handle(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--export-robot-access")) {
            export(args);
            return true;
        }
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

    private static void export(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Expected --export-robot-access output-file [configuration-file]");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(args.length == 3 ? args[2] : "jftn.conf");
        RobotAccess.source(config);
        // Refuse to create an empty database when run from the wrong working directory.
        OrmManager orm = new OrmManager(DatabasePlugin.DEFAULT_DATABASE_URL + ";IFEXISTS=TRUE");
        try {
            orm.connect();
            int count = RobotRuleSnapshot.write(Path.of(args[1]), new DatabaseRobotRules(orm));
            System.out.println("Exported robot access rules: " + count);
            System.out.println("Load the exported fragment in conf.d to select files mode. Other configuration files were not changed.");
        } finally {
            orm.disconnect();
        }
    }
}
