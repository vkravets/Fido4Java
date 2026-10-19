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
import java.util.Properties;

class BsoOutboundTest {
    @TempDir
    Path root;

    private JFtnConfig config() throws Exception {
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nBsoEnabled=true\nBsoOutbound="
                      + root.resolve("bso")
                      + "\nLink=1:500/101,1:500/100,secret,localhost\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    @Test
    void usesBinkleyZonePointAndFlavourNames() throws Exception {
        BsoOutbound bso = new BsoOutbound(config());
        assertEquals(root.resolve("bso/01f40065.flo"),
                     bso.control(new FTNAddr("1:500/101"), "flo"));
        assertEquals(root.resolve("bso.002/01f40065.pnt/00000003.hlo"),
                     bso.control(new FTNAddr("2:500/101.3"), "hlo"));
    }

    @Test
    void publishesPairsOnceAndRequiresBothExternalCopiesToBeConsumed() throws Exception {
        JFtnConfig config = config();
        Link peer = config.getConfiguredLinks().get(0);
        BsoOutbound bso = new BsoOutbound(config);
        Path payload = Files.writeString(root.resolve("LIST.001"), "payload");
        Path tic = Files.writeString(root.resolve("12345678.tic"), "test TIC");
        bso.enqueue("job", peer, List.of(payload, tic), "normal");
        bso.enqueue("job", peer, List.of(payload, tic), "normal");
        Path flo = bso.control(peer.getAddr(), "flo");
        List<String> lines = Files.readAllLines(flo);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("LIST.001"));
        assertTrue(lines.get(1).endsWith("12345678.tic"));
        assertEquals("payload", Files.readString(Path.of(lines.get(0).substring(1))));
        Files.delete(Path.of(lines.get(0).substring(1)));
        assertFalse(bso.acknowledged("job", peer.getAddr()));
        Files.delete(Path.of(lines.get(1).substring(1)));
        Files.delete(flo);
        assertTrue(new BsoOutbound(config).acknowledged("job", peer.getAddr()));
        bso.enqueue("job", peer, List.of(payload, tic), "normal");
        assertFalse(Files.exists(flo));
        assertEquals("payload", Files.readString(payload));
    }

    @Test
    void respectsBusyOwnersAndRecoversPublicationWithoutDuplicatingFloEntries() throws Exception {
        JFtnConfig config = config();
        Link peer = config.getConfiguredLinks().get(0);
        BsoOutbound bso = new BsoOutbound(config);
        Path busy = bso.control(peer.getAddr(), "bsy");
        Files.createDirectories(busy.getParent());
        Files.writeString(busy, "another mailer");
        Path payload = Files.writeString(root.resolve("12345678.pkt"), "packet");
        assertThrows(IOException.class, () -> bso.enqueue("job", peer, List.of(payload), "crash"));
        assertEquals("another mailer", Files.readString(busy));
        assertFalse(bso.acknowledged("job", peer.getAddr()));
        Files.delete(busy);
        bso.enqueue("job", peer, List.of(payload), "crash");
        Path metadata;
        try (var files = Files.walk(Path.of(config.getTmpDir(), "bso-journal"))) {
            metadata = files.filter(path -> path.getFileName()
                                                .toString()
                                                .equals("delivery.properties"))
                            .findFirst()
                            .orElseThrow();
        }
        Properties state = new Properties();
        try (var input = Files.newInputStream(metadata)) {
            state.load(input);
        }
        state.setProperty("state", "PREPARED");
        try (var output = Files.newOutputStream(metadata)) {
            state.store(output, "Simulated crash after FLO write");
        }
        new BsoOutbound(config).enqueue("job", peer, List.of(payload), "crash");
        assertEquals(1, Files.readAllLines(bso.control(peer.getAddr(), "clo")).size());
        assertFalse(Files.exists(busy));
    }

    @Test
    void externalPacketCompletionUpdatesSqlWithoutOfferingThePacketToNativeBinkp()
            throws Exception {
        JFtnConfig config = config();
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:bso"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        try {
            Tosser tosser = new Tosser(config, base);
            Message message = new Message("Author",
                                          "Reader",
                                          new FTNAddr("1:500/100"),
                                          new FTNAddr("1:500/101"),
                                          "Subject",
                                          "Body\r",
                                          new Date());
            tosser.submitNetmail(message, "external");
            tosser.exportPendingNetmail();
            tosser.exportPendingNetmail();
            Link peer = config.getConfiguredLinks().get(0);
            assertEquals(List.of(), new OutboundSpool(config).pending(peer, true));
            Path flo = new BsoOutbound(config).control(peer.getAddr(), "flo");
            List<String> lines = Files.readAllLines(flo);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).matches(".*[0-9a-f]{8}\\.pkt"));
            Files.delete(Path.of(lines.get(0).substring(1)));
            Files.delete(flo);
            tosser.exportPendingNetmail();
            assertTrue(base.getQueuedNetmail().isEmpty());
        } finally {
            base.close();
        }
    }
}
