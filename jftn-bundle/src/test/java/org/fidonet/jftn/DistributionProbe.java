package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.script.MailScripts;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/** Executed in a fresh JVM with only packaged JARs and this test class on the classpath. */
public final class DistributionProbe {
    public static void main(String[] args) throws Exception {
        Set<String> plugins = new HashSet<>();
        for (Plugin<?> plugin : ServiceLoader.load(Plugin.class)) {
            plugins.add(plugin.getPluginInfo().getId());
        }
        if (!plugins.containsAll(Set.of("database",
                                        "schedule",
                                        "binkp_netty",
                                        "jam-bridge",
                                        "fileecho",
                                        "nodelist"))) {
            throw new IllegalStateException("Missing packaged plugins: " + plugins);
        }
        ScriptEngine groovy = new ScriptEngineManager().getEngineByName("groovy");
        if (groovy == null || !groovy.eval("40 + 2").equals(42)) {
            throw new IllegalStateException("Packaged Groovy engine did not work");
        }
        Class.forName("org.h2.Driver");
        URL script = DistributionProbe.class.getResource("/mail-scripts/areafix.groovy");
        if (script == null || !script.getProtocol().equals("file")) {
            throw new IllegalStateException("Mail scripts must be loaded from the external scripts directory");
        }
        verifyDefaultMailScripts();
        verifyOfflineAdministration();
        System.out.println("Packaged plugins, Groovy, H2 and external scripts verified");
    }

    private static void verifyOfflineAdministration() throws Exception {
        Files.writeString(Path.of("conf.d/41-areafix.conf"),
                          "EchoSubscriptions=database\nAreaFixPeer=1:500/101|probe-robot-password|test.*|READ\n");
        JFtnRunner.main(new String[]{"--check-robot-access"});
        if (Files.exists(Path.of("database/jftn10.mv.db"))) {
            throw new IllegalStateException("File validation must not open the mail database");
        }
        verifyExportRequiresExistingDatabase();
        JFtnRunner.main(new String[]{"--import-robot-access"});
        if (!Files.exists(Path.of("database/jftn10.mv.db"))
                || Files.exists(Path.of("database/rpc-auth.mv.db"))) {
            throw new IllegalStateException("Offline import must use the mail DB without creating UI accounts");
        }
        JFtnConfig config = new JFtnConfig();
        config.load("jftn.conf");
        if (!config.getValue("RobotAccessSource").equals("database")) {
            throw new IllegalStateException("Import must not switch the active source automatically");
        }
        verifyOfflineExport();
    }

    private static void verifyExportRequiresExistingDatabase() throws Exception {
        Path output = Path.of("not-exported.conf");
        try {
            JFtnRunner.main(new String[]{"--export-robot-access", output.toString()});
            throw new IllegalStateException("Export must refuse a missing database");
        } catch (SQLException expected) {
            if (Files.exists(output) || Files.exists(Path.of("database/jftn10.mv.db"))) {
                throw new IllegalStateException("Export must not create a database or output on failure");
            }
        }
    }

    private static void verifyOfflineExport() throws Exception {
        Path output = Path.of("conf.d/49-robot-access.conf");
        JFtnRunner.main(new String[]{"--export-robot-access", output.toString()});
        JFtnConfig config = new JFtnConfig();
        config.load("jftn.conf");
        FileRobotRules rules = new FileRobotRules(config);
        if (!config.getValue("RobotAccessSource").equals("files")
                || !rules.find(RobotService.AreaFix, new FTNAddr("1:500/101"))
                         .verifyPassword("probe-robot-password")) {
            throw new IllegalStateException("Exported rules must authorize with the original password in files mode");
        }
        JFtnRunner.main(new String[]{"--check-robot-access"});
        if (Files.readString(output).contains("probe-robot-password")
                || Files.exists(Path.of("database/rpc-auth.mv.db"))) {
            throw new IllegalStateException("Offline export must retain verifiers without starting RPC accounts");
        }
    }

    private static void verifyDefaultMailScripts() throws Exception {
        NodeDirectories.defaults(Path.of("."));
        Path configuration = Path.of("jftn.conf");
        Files.writeString(configuration,
                          "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=mail/inbound\nOutbound=mail/outbound\nTmp=mail/tmp\nEchoPath=mail/echo\n");
        JFtnConfig config = new JFtnConfig();
        config.load(configuration.toString());
        if (!config.getValue("RobotAccessSource").equals("database")) {
            throw new IllegalStateException("The full distribution must default to database rules");
        }
        if (config.getValuesAsList("MailScript").size() != 3) {
            throw new IllegalStateException("Expected three default handlers");
        }
        for (String name : config.getValuesAsList("MailScript")) {
            if (name.startsWith("classpath:") || !Files.isRegularFile(Path.of(name))) {
                throw new IllegalStateException("Default scripts must use external paths: " + name);
            }
        }
        DatabaseManager database = new DatabaseManager(new OrmManager("jdbc:h2:mem:defaultScripts"));
        try {
            if (!database.open()) {
                throw new IllegalStateException("Cannot initialize script test database");
            }
            Tosser tosser = new Tosser(config, database);
            try (MailScripts scripts = new MailScripts(config, database, tosser)) {
                scripts.beforeExport(tosser);
            }
            if (Files.exists(Path.of("database/nodelist"))) {
                throw new IllegalStateException("Unconfigured directory feeds must not open a catalog");
            }
        } finally {
            database.close();
        }
    }
}
