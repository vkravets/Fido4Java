package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.NetmailRoute;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class NetmailPolicyTest {
    @TempDir
    public File temporary;

    private JFtnConfig config(String extra) throws Exception {
        String root = temporary.getPath().replace('\\', '/');
        String text = "Inbound="
                      + root
                      + "/in\nOutbound="
                      + root
                      + "/out\nTmp="
                      + root
                      + "/state\nEchoPath="
                      + root
                      + "/echo\nSysOp=Test\n"
                      + "Link=1:500/101,1:500/102,secret,localhost\n"
                      + "Link=1:500/103,1:500/102,secret,localhost\n"
                      + extra;
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private Message message(String destination, String flags) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("1:500/102"),
                           new FTNAddr(destination),
                           "Subject",
                           flags + "Hello\r",
                           new Date());
    }

    @Test
    public void explainsOrderedMasksDirectFallbackAndLocalDelivery() throws Exception {
        NetmailRouter router = new NetmailRouter(config("Route=1:500/9??,1:500/103\n"
                + "Route=1:500/*,1:500/101\nDefaultRoute=1:500/101\n"));
        NetmailDecision first = router.explain(message("1:500/999", ""));
        assertEquals(NetmailDecision.Reason.RULE, first.getReason());
        assertEquals("config:0", first.getRuleId());
        assertEquals(new FTNAddr("1:500/103"), first.getNextHop().getAddr());
        assertEquals(NetmailDecision.Reason.LOCAL,
                     router.explain(message("1:500/102", "")).getReason());
        NetmailRouter fallback = new NetmailRouter(config("DefaultRoute=1:500/101\n"));
        assertEquals(NetmailDecision.Reason.DIRECT,
                     fallback.explain(message("1:500/103", "")).getReason());
        assertEquals(NetmailDecision.Reason.DEFAULT,
                     fallback.explain(message("1:500/999.1", "")).getReason());
        assertEquals(NetmailDecision.Reason.NO_ROUTE,
                     new NetmailRouter(config("")).explain(message("1:500/999", "")).getReason());
    }

    @Test
    public void directAndCrashNeverFallBackToARoutedLink() throws Exception {
        NetmailRouter router = new NetmailRouter(config("DefaultRoute=1:500/101\n"));
        for (String flags : new String[]{"\1FLAGS: DIR\r", "\1FLAGS: CRA\r", "\1FLAGS: HLD\r",
                "\1FLAGS: IMM\r"}) {
            assertEquals(NetmailDecision.Reason.DIRECT_UNAVAILABLE,
                         router.explain(message("1:500/999", flags)).getReason());
        }
        Message crash = message("1:500/999", "");
        crash.setAttributes(2);
        assertNull(router.route(crash));
        assertEquals(new FTNAddr("1:500/103"),
                     router.route(message("1:500/103", "\1FLAGS: DIR\r")).getAddr());
    }

    @Test
    void huskyPriorityOrderBypassesRoutesAndOnlyPureHoldIsHeld() throws Exception {
        NetmailRouter router = new NetmailRouter(config("Route=1:500/*,1:500/101\n"));
        for (String flags : new String[]{"HLD", "CRA HLD", "DIR HLD", "IMM HLD"}) {
            Message mail = message("1:500/103", "\1FLAGS: " + flags + "\r");
            assertEquals(new FTNAddr("1:500/103"), router.route(mail).getAddr());
        }
        assertEquals(NetmailPriority.HOLD,
                     NetmailPriority.of(message("1:500/103", "\1FLAGS: HLD\r")));
        assertEquals(NetmailPriority.DIRECT,
                     NetmailPriority.of(message("1:500/103", "\1FLAGS: CRA HLD\r")));
        assertEquals(NetmailPriority.DIRECT,
                     NetmailPriority.of(message("1:500/103", "\1FLAGS: DIR HLD\r")));
        assertEquals(NetmailPriority.IMMEDIATE,
                     NetmailPriority.of(message("1:500/103", "\1FLAGS: IMM DIR HLD\r")));
        Message hold = message("1:500/999", "");
        hold.setAttributes(512);
        assertEquals(NetmailDecision.Reason.DIRECT_UNAVAILABLE, router.explain(hold).getReason());
    }

    @Test
    void resolvesHostBossHubAndNorouteTargetsLikeHpt() throws Exception {
        String links = "Link=1:500/0,1:500/102,secret,localhost\n"
                       + "Link=1:500/300,1:500/102,secret,localhost\n";
        NetmailRouter host = new NetmailRouter(config(links + "Route=1:500/*,host\n"));
        assertEquals(new FTNAddr("1:500/0"), host.route(message("1:500/999.4", "")).getAddr());
        NetmailRouter boss = new NetmailRouter(config("Route=1:500/*,boss\n"));
        assertEquals(new FTNAddr("1:500/101"), boss.route(message("1:500/101.4", "")).getAddr());
        NetmailRouter hub = new NetmailRouter(config(links + "Route=1:500/*,hub\n"));
        assertEquals(new FTNAddr("1:500/300"), hub.route(message("1:500/399.4", "")).getAddr());
        NetmailRouter direct = new NetmailRouter(config("Route=1:500/*,noroute\n"));
        assertEquals(new FTNAddr("1:500/103"), direct.route(message("1:500/103", "")).getAddr());
    }

    @Test
    void nopackSurvivesRestartAndFreezesNextHopOnlyWhenReleasedByRouting() throws Exception {
        JFtnConfig config = config("NetmailRoutes=database\n");
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:nopack"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        try {
            Tosser tosser = new Tosser(config, base);
            tosser.getNetmailRouter()
                  .saveRule(new NetmailRoute("hold", 0, "1:500/*", "nopack", true));
            tosser.submitNetmail(message("1:500/999", ""), "unpacked");
            tosser = new Tosser(config, base);
            tosser.exportPendingNetmail();
            assertEquals("nopack", base.getQueuedNetmail().get(0).getNextHop());
            tosser.getNetmailRouter()
                  .saveRule(new NetmailRoute("hold", 0, "1:500/*", "1:500/101", true));
            tosser.exportPendingNetmail();
            assertEquals("1:500/101.0", base.getQueuedNetmail().get(0).getNextHop());
            tosser.getNetmailRouter()
                  .saveRule(new NetmailRoute("hold", 0, "1:500/*", "1:500/103", true));
            tosser.exportPendingNetmail();
            assertEquals("1:500/101.0", base.getQueuedNetmail().get(0).getNextHop());
            OutboundSpool spool = new OutboundSpool(config);
            assertEquals(1, spool.pending(config.getLink(new FTNAddr("1:500/101")), true).size());
            assertEquals(0, spool.pending(config.getLink(new FTNAddr("1:500/103")), true).size());
        } finally {
            base.close();
        }
    }

    @Test
    public void rejectsInvalidRulesBeforeProcessingAnyMail() throws Exception {
        for (String settings : new String[]{"Route=bad,1:500/101\n", "Route=1:500/*,1:500/999\n",
                "Route=1:500/*,\n", "DefaultRoute=1:500/999\n", "TransitFrom=bad\n"}) {
            try {
                new NetmailRouter(config(settings));
                fail("Invalid configuration accepted: " + settings);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().length() > 0);
            }
        }
    }

    @Test
    void holdCombinesWithCrashDirectAndImmediateLikeHuskyOutboundFlavours() throws Exception {
        JFtnConfig config = config("");
        OutboundSpool spool = new OutboundSpool(config);
        for (String flags : new String[]{"HLD", "CRA HLD", "DIR HLD", "IMM HLD"}) {
            spool.enqueue("netmail:" + flags,
                          config.getLink(new FTNAddr("1:500/103")),
                          message("1:500/103", "\1FLAGS: " + flags + "\r"));
        }
        assertEquals(4, spool.pending(config.getLink(new FTNAddr("1:500/103")), true).size());
        assertEquals(3, spool.pending(config.getLink(new FTNAddr("1:500/103")), false).size());
    }

    @Test
    public void explainsTransitPermissionsReturnAndViaLoops() throws Exception {
        NetmailRouter router = new NetmailRouter(config("AllowTransit=true\n"
                + "TransitFrom=1:500/101\nTransitTo=1:500/103\nDefaultRoute=1:500/103\n"));
        Message mail = message("1:500/999", "");
        assertTrue(router.explainTransit(mail, new FTNAddr("1:500/101")).isPermitted());
        assertEquals(NetmailDecision.Reason.SOURCE_DENIED,
                     router.explainTransit(mail, new FTNAddr("1:500/104")).getReason());
        Message loop = message("1:500/999", "");
        loop.getBody().addVia("\1Via 1:500/102 @20261015.120000.UTC Test 1");
        assertEquals(NetmailDecision.Reason.VIA_LOOP,
                     router.explainTransit(loop, new FTNAddr("1:500/101")).getReason());
        NetmailRouter returning = new NetmailRouter(config("AllowTransit=true\nDefaultRoute=1:500/101\n"));
        assertEquals(NetmailDecision.Reason.RETURN_TO_SOURCE,
                     returning.explainTransit(mail, new FTNAddr("1:500/101")).getReason());
        NetmailRouter blocked = new NetmailRouter(config("DefaultRoute=1:500/103\n"));
        assertEquals(NetmailDecision.Reason.TRANSIT_DISABLED,
                     blocked.explainTransit(mail, new FTNAddr("1:500/101")).getReason());
        NetmailRouter targetDenied = new NetmailRouter(config("AllowTransit=true\n"
                + "TransitTo=1:500/101\nDefaultRoute=1:500/103\n"));
        assertEquals(NetmailDecision.Reason.TARGET_DENIED,
                     targetDenied.explainTransit(mail, new FTNAddr("1:500/101")).getReason());
    }

    @Test
    public void persistsRulesAndDoesNotRerouteAcceptedDeliveries() throws Exception {
        String url = "jdbc:h2:mem:routes" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager(url));
        assertTrue(base.open());
        JFtnConfig config = config("NetmailRoutes=database\n");
        try {
            Tosser tosser = new Tosser(config, base);
            NetmailRouter router = tosser.getNetmailRouter();
            router.saveRule(new NetmailRoute("first", 0, "1:500/*", "1:500/101", true));
            tosser.submitNetmail(message("1:500/999", ""), "queued");
            router.saveRule(new NetmailRoute("first", 0, "1:500/*", "1:500/103", true));
            assertEquals(new FTNAddr("1:500/103"),
                         router.route(message("1:500/999", "")).getAddr());
            assertEquals("1:500/101.0", base.getQueuedNetmail().get(0).getNextHop());
            tosser.exportPendingNetmail();
            assertEquals(1,
                         new OutboundSpool(config).pending(config.getLink(new FTNAddr("1:500/101")),
                                                           true)
                                                  .size());
        } finally {
            base.close();
        }
        MailDatabaseFacade reopened = new MailDatabaseFacade(new OrmManager(url));
        assertTrue(reopened.open());
        try {
            NetmailRouter router = new NetmailRouter(config, reopened);
            assertEquals("first", router.rules().get(0).getId());
            router.saveRule(new NetmailRoute("first", 0, "1:500/*", "1:500/103", false));
            assertNull(router.route(message("1:500/999", "")));
            router.removeRule("first");
            assertTrue(router.rules().isEmpty());
        } finally {
            reopened.close();
        }
    }
}
