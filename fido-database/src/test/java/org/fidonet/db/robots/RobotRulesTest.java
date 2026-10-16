package org.fidonet.db.robots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.OrmManager;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

class RobotRulesTest {
    @TempDir
    Path root;

    private JFtnConfig config(String rules) throws Exception {
        JFtnConfig config = new JFtnConfig();
        String text = "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                + rules;
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private RobotAccessRule rule(String password, boolean enabled) {
        return RobotAccessRule.create(RobotService.AreaFix,
                                      "1:500/101",
                                      password,
                                      "test.*,local.news",
                                      "READ",
                                      enabled);
    }

    @Test
    void fileSourceValidatedRulesAndPreservedServicePasswordSemantics() throws Exception {
        FileRobotRules rules = new FileRobotRules(config("AreaFixPeer=1:500/101|CaseSensitive|test.*|READ\nTicPeer=1:500/101|TicPassword|lists\n"));
        RobotAccessRule area = rules.find(RobotService.AreaFix, new FTNAddr("1:500/101"));
        assertTrue(area.verifyPassword("CaseSensitive"));
        assertFalse(area.verifyPassword("casesensitive"));
        assertTrue(area.allows("TEST.ONE"));
        assertFalse(area.allows("private"));
        assertThrows(IllegalStateException.class, area::getDeliveryPassword);
        assertTrue(rules.find(RobotService.Tic, new FTNAddr("1:500/101"))
                        .verifyPassword("ticpassword"));
        assertNull(rules.find(RobotService.FileFix, new FTNAddr("1:500/101")));
        assertThrows(IllegalArgumentException.class,
                     () -> new FileRobotRules(config("FileFixPeer=1:500/999|secret|*\n")));
        assertThrows(IllegalArgumentException.class,
                     () -> new FileRobotRules(config("AreaFixPeer=1:500/101|secret|*|WRITE\n")));
        assertThrows(IllegalArgumentException.class,
                     () -> new FileRobotRules(config("TicPeer=1:500/101|one|*\nTicPeer=1:500/101|two|*\n")));
    }

    @Test
    void databaseRulesPersistedAndUpdatesAndRevocationWereVisibleImmediately() throws Exception {
        String url = "jdbc:h2:" + root.resolve("rules");
        OrmManager first = new OrmManager(url);
        first.connect();
        try {
            DatabaseRobotRules store = new DatabaseRobotRules(first);
            store.save(rule("initial-password", true));
            assertFalse(first.<RobotRuleRecord, String>getDao(RobotRuleRecord.class)
                             .queryForAll()
                             .get(0)
                             .toRule()
                             .credential()
                             .contains("initial-password"));
        } finally {
            first.disconnect();
        }
        OrmManager reopened = new OrmManager(url);
        reopened.connect();
        try {
            DatabaseRobotRules store = new DatabaseRobotRules(reopened);
            FTNAddr peer = new FTNAddr("1:500/101");
            assertTrue(store.find(RobotService.AreaFix, peer).verifyPassword("initial-password"));
            store.save(rule("replacement-password", true));
            assertFalse(store.find(RobotService.AreaFix, peer).verifyPassword("initial-password"));
            store.save(rule("replacement-password", false));
            assertNull(store.find(RobotService.AreaFix, peer));
            store.delete(RobotService.AreaFix, peer);
            assertTrue(store.list(RobotService.AreaFix).isEmpty());
        } finally {
            reopened.disconnect();
        }
    }

    @Test
    void explicitImportWasAtomicAndNeverOverwroteExistingRules() throws Exception {
        OrmManager orm = new OrmManager("jdbc:h2:mem:robot-import" + System.nanoTime());
        orm.connect();
        try {
            DatabaseRobotRules store = new DatabaseRobotRules(orm);
            RobotAccessRule rule = rule("password", true);
            assertThrows(Exception.class, () -> store.importIntoEmpty(List.of(rule, rule)));
            assertTrue(store.list(RobotService.AreaFix).isEmpty());
            store.importIntoEmpty(List.of(rule));
            assertNotNull(store.find(RobotService.AreaFix, new FTNAddr("1:500/101")));
            assertThrows(Exception.class,
                         () -> store.importIntoEmpty(List.of(rule("new-password", true))));
            assertTrue(store.find(RobotService.AreaFix, new FTNAddr("1:500/101"))
                            .verifyPassword("password"));
            assertEquals(1, store.list(RobotService.AreaFix).size());
        } finally {
            orm.disconnect();
        }
    }
}
