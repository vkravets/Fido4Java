package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.script.RobotAccess;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

class RobotAccessTest {
    @TempDir
    Path root;

    private String text(String source, String rules) {
        return "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\nRpcEnabled=false\nRobotAccessSource="
                + source + "\n" + rules;
    }

    private JFtnConfig config(String source, String rules) throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text(source, rules).getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    @Test
    void filesWorkedWithoutADatabaseOrRpcAndUnknownModesFailedClosed() throws Exception {
        RobotAccess access = new RobotAccess(config("files",
                                                    "FileFixPeer=1:500/101|file-password|lists\n"),
                                             null);
        assertNotNull(access.authorize("FileFix", new FTNAddr("1:500/101"), "file-password"));
        assertNull(access.authorize("FileFix", new FTNAddr("1:500/101"), "wrong"));
        assertNull(access.find("FileFix", new FTNAddr("1:500/999")));
        assertThrows(IllegalArgumentException.class,
                     () -> new RobotAccess(config("auto", ""), null));
    }

    @Test
    void defaultDatabaseModeNeverFellBackToFilesAndObservedRevocationWithoutRestart()
            throws Exception {
        DatabaseManager database = new DatabaseManager(new OrmManager("jdbc:h2:mem:robot-source"
                                                                      + System.nanoTime()));
        assertTrue(database.open());
        try {
            JFtnConfig config = new JFtnConfig();
            String configuration = text("database",
                                        "FileFixPeer=ignored inactive malformed entry\n").replace("RobotAccessSource=database\n",
                                                                                                  "");
            config.load(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));
            assertEquals("database", RobotAccess.source(config));
            RobotAccess access = new RobotAccess(config, database);
            FTNAddr peer = new FTNAddr("1:500/101");
            assertNull(access.authorize("FileFix", peer, "file-password"));
            database.getRobotRules()
                    .save(RobotAccessRule.create(RobotService.FileFix,
                                                 peer.toString(),
                                                 "database-password",
                                                 "lists",
                                                 "",
                                                 true));
            assertNotNull(access.authorize("FileFix", peer, "database-password"));
            assertNull(access.authorize("FileFix", peer, "file-password"));
            database.getRobotRules().delete(RobotService.FileFix, peer);
            assertNull(access.authorize("FileFix", peer, "database-password"));
        } finally {
            database.close();
        }
    }

    @Test
    void exportRequiredAnExplicitDestinationBeforeOpeningConfigurationOrDatabase() {
        assertThrows(IllegalArgumentException.class,
                     () -> RobotAccessCommand.handle(new String[]{"--export-robot-access"}));
        assertThrows(IllegalArgumentException.class,
                     () -> RobotAccessCommand.handle(new String[]{"--export-robot-access", "out",
                             "config", "extra"}));
    }

    @Test
    void offlineFileCheckLoadedFragmentsWithoutStartingTheSetupWizard() throws Exception {
        Path main = Files.writeString(root.resolve("jftn.conf"), text("files", ""));
        Files.createDirectory(root.resolve("conf.d"));
        Path fragment = root.resolve("conf.d/41-areafix.conf");
        String rule = "AreaFixPeer=1:500/101|private-robot-password|test.*|READ\n";
        Files.writeString(fragment, rule);
        assertTrue(RobotAccessCommand.handle(new String[]{"--check-robot-access",
                main.toString()}));
        assertEquals(rule, Files.readString(fragment));
        try (Stream<Path> paths = Files.list(root)) {
            assertEquals(2, paths.count());
        }
    }
}
