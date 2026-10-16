package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.rpc.RobotRuleChange;
import org.fidonet.jftn.rpc.RobotRuleInfo;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class NodeRobotRulesTest {
    private DatabaseManager base;

    @BeforeEach
    void open() {
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:robot-admin" + System.nanoTime()));
        assertTrue(base.open());
    }

    @AfterEach
    void close() {
        base.close();
    }

    private NodeRobotRules api(String source) throws Exception {
        String text = "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                + "RobotAccessSource=" + source
                + "\nAreaFixPeer=1:500/101|file-secret|test.*|READ\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return new NodeRobotRules(config, base);
    }

    private RobotRuleChange change(String service, String password) {
        return new RobotRuleChange(new RobotRuleInfo(service,
                                                     "1:500/101",
                                                     "test.*",
                                                     "READ",
                                                     true)).setPassword(password);
    }

    @Test
    void rotatesPasswordsAndPreservesDisabledCredentialsForAllServices() throws Exception {
        NodeRobotRules api = api("database");
        FTNAddr peer = new FTNAddr("1:500/101");
        for (RobotService service : RobotService.values()) {
            RobotRuleChange change = change(service.name(), "initial");
            api.save(change);
            change.getRule().setEnabled(false);
            change.unsetPassword();
            api.save(change);
            assertNull(base.getRobotRules().find(service, peer));
            change.getRule().setEnabled(true).setMasks("other.*");
            api.save(change);
            assertTrue(base.getRobotRules().find(service, peer).verifyPassword("initial"));
            assertTrue(base.getRobotRules().find(service, peer).allows("other.one"));
            api.save(change.setPassword("replacement"));
            assertFalse(base.getRobotRules().find(service, peer).verifyPassword("initial"));
            assertTrue(base.getRobotRules().find(service, peer).verifyPassword("replacement"));
        }
        assertEquals(3, api("database").current().getRulesSize());
    }

    @Test
    void fileRulesAreVisibleButCannotBeModifiedOrImportedThroughTheUi() throws Exception {
        NodeRobotRules api = api("files");
        assertEquals("files", api.current().getSource());
        assertEquals(1, api.current().getRulesSize());
        assertEquals("READ_ONLY",
                     assertThrows(UiError.class,
                                  () -> api.save(change("AreaFix", "replacement"))).getCode());
        assertEquals("READ_ONLY",
                     assertThrows(UiError.class,
                                  () -> api.delete("AreaFix", "1:500/101")).getCode());
        assertTrue(base.getRobotRules().list(RobotService.AreaFix).isEmpty());
    }

    @Test
    void rejectsInvalidChangesWithoutOverwritingExistingRules() throws Exception {
        NodeRobotRules api = api("database");
        api.save(change("AreaFix", "initial"));
        RobotRuleChange invalid = change("AreaFix", "replacement");
        invalid.getRule().setMasks("valid,,invalid");
        assertThrows(IllegalArgumentException.class, () -> api.save(invalid));
        assertThrows(IllegalArgumentException.class, () -> api.save(null));
        assertThrows(IllegalArgumentException.class, () -> api.save(change("Unknown", "secret")));
        assertTrue(base.getRobotRules()
                       .find(RobotService.AreaFix, new FTNAddr("1:500/101"))
                       .verifyPassword("initial"));
    }
}
