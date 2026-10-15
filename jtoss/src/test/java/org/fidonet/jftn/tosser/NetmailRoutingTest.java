package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.fts.FtsPkt;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;

public class NetmailRoutingTest {
    @TempDir
    public File temporary;

    JFtnConfig config(boolean transit) throws Exception {
        String root = Files.createTempDirectory(temporary.toPath(), "test-")
                           .toFile()
                           .getAbsolutePath()
                           .replace('\\', '/');
        String text = "Inbound="
                      + root
                      + "/inbound\nTmp="
                      + root
                      + "/tmp\nEchoPath="
                      + root
                      + "/echo\n"
                      + "Outbound="
                      + root
                      + "/outbound\nSysOp=Test\nAllowTransit="
                      + transit
                      + "\n"
                      + "Link=2:500/101,2:500/102,secret,localhost\n"
                      + "Link=2:500/103,2:500/102,secret,localhost\nDefaultRoute=2:500/103\n";
        Files.createDirectories(Path.of(root, "inbound"));
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private Message message(String from, String body) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr(from),
                           new FTNAddr("3:600/1.4"),
                           "Subject",
                           body,
                           new Date());
    }

    @Test
    public void importsUnambiguousLegacyType2PacketWithoutZoneFields() throws Exception {
        JFtnConfig config = config(false);
        DatabaseManager base = new DatabaseManager(new OrmManager("jdbc:h2:mem:legacyPacket"
                                                                  + System.nanoTime()));
        assertTrue(base.open());
        try {
            Message local = new Message("Author",
                                        "Reader",
                                        new FTNAddr("2:500/101"),
                                        new FTNAddr("2:500/102"),
                                        "Subject",
                                        "Legacy packet\r",
                                        new Date());
            byte[] bytes = PacketWriter.write(local.getFAddr(),
                                              local.getTAddr(),
                                              "secret",
                                              Collections.singletonList(local));
            ByteBuffer raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            raw.putShort(34, (short) 0);
            raw.putShort(36, (short) 0);
            raw.putShort(40, (short) 0);
            raw.putShort(44, (short) 0);
            raw.putShort(46, (short) 0);
            raw.putShort(48, (short) 0);
            Files.write(Path.of(config.getInbound(), "legacy.pkt"), bytes);
            new Tosser(config, base).runFast(config.getInbound());
            assertEquals(1, base.getLocalNetmail(local.getTAddr()).size());
        } finally {
            base.close();
        }
    }

    @Test
    public void persistsRoutesExportsAndAcknowledgesOutgoingNetmail() throws Exception {
        JFtnConfig config = config(false);
        DatabaseManager base = new DatabaseManager(new OrmManager("jdbc:h2:mem:route"
                                                                  + System.nanoTime()));
        assertTrue(base.open());
        try {
            Tosser tosser = new Tosser(config, base);
            Message message = message("2:500/102", "\1MSGID: 2:500/102 outgoing\rHello\r");
            message.setAttributes(512);
            tosser.submitNetmail(message, "outgoing");
            tosser.exportPendingNetmail();
            tosser.exportPendingNetmail();
            Link nextHop = config.getLink(new FTNAddr("2:500/103"));
            OutboundSpool spool = new OutboundSpool(config);
            assertTrue(spool.pending(nextHop, false).isEmpty());
            assertEquals(1, spool.pending(nextHop, true).size());
            Path file = spool.pending(nextHop, true).get(0);
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(file)));
            assertEquals(103, packet.getDestNode());
            Message restored = new Message(packet.getMsgs()[0],
                                           packet.getOrigZone(),
                                           packet.getDestZone());
            assertEquals(message.getTAddr(), restored.getTAddr());
            assertTrue(restored.getBody().toString().contains("Hello\r\1Via 2:500/102"));
            assertEquals(1, base.getQueuedNetmail().size());
            spool.acknowledge(file);
            // Simulate a crash after the filesystem receipt but before the SQL update.
            new Tosser(config, base).exportPendingNetmail();
            assertTrue(base.getQueuedNetmail().isEmpty());
            tosser.exportPendingNetmail();
            assertTrue(spool.pending(nextHop, true).isEmpty());
        } finally {
            base.close();
        }
    }

    @Test
    public void onlyRoutesTransitWhenExplicitlyAllowed() throws Exception {
        for (boolean allowed : new boolean[]{false, true}) {
            JFtnConfig config = config(allowed);
            DatabaseManager base = new DatabaseManager(new OrmManager("jdbc:h2:mem:transit"
                                                                      + System.nanoTime()));
            assertTrue(base.open());
            try {
                byte[] bytes = PacketWriter.write(new FTNAddr("2:500/101"),
                                                  new FTNAddr("2:500/102"),
                                                  "secret",
                                                  Collections.singletonList(message("1:100/1",
                                                                                    "Transit\r")));
                Files.write(Path.of(config.getInbound(), "transit.pkt"), bytes);
                new Tosser(config, base).runFast(config.getInbound());
                assertEquals(allowed ? 1 : 0, base.getQueuedNetmail().size());
                assertEquals(!allowed, Files.exists(Path.of(config.getTmpDir(), "transit.bad")));
            } finally {
                base.close();
            }
        }
    }
}
