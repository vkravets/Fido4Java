package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.RuntimeProfile;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.db.robots.RobotService;
import org.fidonet.echobase.MailStorage;
import org.fidonet.mail.files.FileMailStore;
import org.fidonet.mail.files.LegacyMailStore;

import java.util.Locale;
import java.util.Set;

/** Offline SQL-free processing uses the same runtime lock, scripts and queues as the server. */
final class ProfileCommands {
    private ProfileCommands() {
    }

    static boolean handle(String[] args, RuntimeProfile profile) throws Exception {
        if (args.length != 1
                || !Set.of("--process-mail", "--check-robot-access").contains(args[0])) {
            return false;
        }
        if (profile.usesSql() && args[0].equals("--check-robot-access")) {
            return false;
        }
        if (profile.usesSql()) {
            throw new IllegalArgumentException("--process-mail is an offline command for legacy/transit profiles");
        }
        JFtnConfig config = new JFtnConfig();
        config.load("jftn.conf");
        profile.validate(config);
        NodeDirectories.configured(config);
        if (args[0].equals("--check-robot-access")) {
            FileRobotRules rules = new FileRobotRules(config);
            for (RobotService service : RobotService.values()) {
                System.out.println(service
                                   + ": "
                                   + rules.list(service)
                                          .size()
                                   + " validated file rules");
            }
            return true;
        }
        MailStorage storage = profile == RuntimeProfile.LEGACY
                ? new LegacyMailStore(config)
                : new FileMailStore(config);
        try {
            MailRuntime.runOnce(config, storage);
        } finally {
            storage.close();
        }
        System.out.println("JFtn "
                           + profile.name()
                                    .toLowerCase(Locale.ROOT)
                           + " mail processing completed");
        return true;
    }
}
