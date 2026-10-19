package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class BsoRequestsTest {
    @TempDir
    Path root;

    private JFtnConfig config() throws Exception {
        String text = "SysOp=Test\nInbound=" + root.resolve("in") + "\nOutbound="
                + root.resolve("out") + "\nTmp=" + root.resolve("tmp") + "\nEchoPath="
                + root.resolve("echo")
                + "\nBsoEnabled=true\nLink=1:500/101,1:500/100,secret,localhost"
                + "\nLink=1:500/102,1:500/100,secret,localhost\nRoute=1:500/*,1:500/102\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private Message request(String destination) {
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("1:500/100"),
                                      new FTNAddr(destination),
                                      "NODELIST.*\tFILES",
                                      "Body\r",
                                      new Date());
        message.setAttributes(0x0801);
        return message;
    }

    @Test
    void routesRequestsDirectlyAndUsesSeparateRouteFileRulesForAttaches() throws Exception {
        JFtnConfig config = config();
        NetmailRouter router = new NetmailRouter(config);
        assertEquals(new FTNAddr("1:500/101"), router.route(request("1:500/101")).getAddr());
        assertEquals(NetmailDecision.Reason.DIRECT_UNAVAILABLE,
                     router.explain(request("1:500/999")).getReason());
        Message attach = request("1:500/101");
        attach.setAttributes(17);
        assertEquals(NetmailDecision.Reason.NO_ROUTE, router.explain(attach).getReason());
    }

    @Test
    void waitsForBothRequestAndPacketConsumptionAcrossRestart() throws Exception {
        JFtnConfig config = config();
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:frq"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        try {
            Tosser tosser = new Tosser(config, base);
            tosser.submitNetmail(request("1:500/101"), "request");
            tosser.exportPendingNetmail();
            tosser.exportPendingNetmail();
            Link peer = config.getLink(new FTNAddr("1:500/101"));
            BsoOutbound bso = new BsoOutbound(config);
            Path req = bso.control(peer.getAddr(), "req");
            assertEquals("NODELIST.*\r\nFILES\r\n", Files.readString(req));
            List<String> lines = Files.readAllLines(bso.control(peer.getAddr(), "flo"));
            assertEquals(1, lines.size());
            Files.delete(Path.of(lines.get(0).substring(1)));
            new Tosser(config, base).exportPendingNetmail();
            assertEquals(1, base.getQueuedNetmail().size());
            Files.delete(req);
            new Tosser(config, base).exportPendingNetmail();
            assertTrue(base.getQueuedNetmail().isEmpty());
        } finally {
            base.close();
        }
    }

    @Test
    void recordsConsumedRequestBatchesBeforePublishingANewBatch() throws Exception {
        JFtnConfig config = config();
        Link peer = config.getConfiguredLinks().get(0);
        BsoOutbound bso = new BsoOutbound(config);
        Path first = Files.writeString(root.resolve("12345678.pkt"), "first");
        List<BsoOutbound.File> files = List.of(new BsoOutbound.File(first, "12345678.pkt"));
        bso.enqueueNamed("first", peer, files, "normal", "ONE.*");
        Path req = bso.control(peer.getAddr(), "req");
        Files.delete(req);
        bso.enqueueNamed("second", peer, files, "normal", "TWO.*");
        List<String> flo = Files.readAllLines(bso.control(peer.getAddr(), "flo"));
        Files.delete(Path.of(flo.get(0).substring(1)));
        assertTrue(bso.acknowledged("first", peer.getAddr()));
        assertFalse(bso.acknowledged("second", peer.getAddr()));
        assertEquals("TWO.*\r\n", Files.readString(req));
        assertThrows(IOException.class,
                     () -> bso.enqueueNamed("second", peer, files, "normal", "DIFFERENT.*"));
    }

    @Test
    void respectsBusyLocksAndRejectsRequestInjection() throws Exception {
        JFtnConfig config = config();
        Link peer = config.getConfiguredLinks().get(0);
        BsoOutbound bso = new BsoOutbound(config);
        Path payload = Files.writeString(root.resolve("12345678.pkt"), "packet");
        Path busy = bso.control(peer.getAddr(), "bsy");
        Files.createDirectories(busy.getParent());
        Files.writeString(busy, "owner");
        List<BsoOutbound.File> files = List.of(new BsoOutbound.File(payload, "12345678.pkt"));
        assertThrows(IOException.class,
                     () -> bso.enqueueNamed("busy", peer, files, "normal", "FILES"));
        assertFalse(Files.exists(bso.control(peer.getAddr(), "req")));
        assertEquals("owner", Files.readString(busy));
        Files.delete(busy);
        bso.enqueueNamed("busy", peer, files, "normal", "FILES");
        assertEquals("FILES\r\n", Files.readString(bso.control(peer.getAddr(), "req")));
        assertThrows(IOException.class, () -> BsoRequests.normalize("ONE\r\nTWO"));
        assertThrows(IOException.class, () -> BsoRequests.normalize("../SECRET"));
    }
}
