package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.echobase.AccessLevel;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;

public class EchoExportTest {
    @TempDir
    public File temporary;

    private JFtnConfig config() throws Exception {
        return config("");
    }

    private JFtnConfig config(String extra) throws Exception {
        String root = temporary.getAbsolutePath().replace('\\', '/');
        String text = "Inbound="
                      + root
                      + "/inbound\nTmp="
                      + root
                      + "/tmp\nEchoPath="
                      + root
                      + "/echo\n"
                      + "Outbound="
                      + root
                      + "/outbound\nSysOp=Test\n"
                      + "Link=2:500/101,2:500/102,secret,localhost\n"
                      + "Link=2:500/103,2:500/102,secret,localhost\n"
                      + "Link=2:500/104,2:500/102,secret,localhost\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream((text + extra).getBytes(StandardCharsets.UTF_8)));
        Files.createDirectories(Path.of(config.getInbound()));
        return config;
    }

    private void subscribe(OrmManager orm, DatabaseManager base, Link link) throws Exception {
        ConfigurationLink configured = new ConfigurationLink();
        configured.setAddress(link.getAddr().as5D());
        configured.setPassword("secret");
        configured.setPacket_password("secret");
        orm.getDao(ConfigurationLink.class).create(configured);
        base.addSubscription(link, "test");
    }

    @Test
    public void exportsOnlyToEligibleSubscribersAndPreservesOriginalMessage() throws Exception {
        JFtnConfig config = config();
        OrmManager orm = new OrmManager("jdbc:h2:mem:echoExport" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            base.createArea("test");
            for (Link link : config.getConfiguredLinks()) {
                subscribe(orm, base, link);
            }
            Message message = new Message("Author",
                                          "All",
                                          new FTNAddr("2:400/1"),
                                          new FTNAddr("2:500/102"),
                                          "Subject",
                                          "AREA:TEST\r\1MSGID: 2:400/1 unique\rHello\r--- Original\r"
                                                  + " * Origin: Original (2:400/1)\rSEEN-BY: 500/104\r\1PATH: 400/1\r",
                                          new Date());
            message.setUpLink(new FTNAddr("2:500/101"));
            base.addMessage(message, "test");
            Tosser tosser = new Tosser(config, base);
            tosser.exportEchomail();
            tosser.exportEchomail();
            OutboundSpool spool = new OutboundSpool(config);
            assertTrue(spool.pending(config.getLink(new FTNAddr("2:500/101")), true).isEmpty());
            assertTrue(spool.pending(config.getLink(new FTNAddr("2:500/104")), true).isEmpty());
            Link target = config.getLink(new FTNAddr("2:500/103"));
            assertEquals(1, spool.pending(target, true).size());
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(spool.pending(target,
                                                                                        true)
                                                                               .get(0))));
            Message exported = new Message(packet.getMsgs()[0],
                                           packet.getOrigZone(),
                                           packet.getDestZone());
            assertEquals(message.getBody().getOrigin(), exported.getBody().getOrigin());
            assertTrue(exported.getBody()
                               .getSeenBy()
                               .getAddresses()
                               .contains(new FTNAddr("2:500/103")));
            assertTrue(exported.getBody()
                               .getPath()
                               .getAddresses()
                               .contains(new FTNAddr("2:500/102")));
            assertEquals(message.getBody().toString(),
                         base.getMessages("test").next().getBody().toString());
            spool.acknowledge(spool.pending(target, true).get(0));
            new Tosser(config, base).exportEchomail();
            assertTrue(spool.pending(target, true).isEmpty());
        } finally {
            base.close();
        }
    }

    @Test
    public void quarantinesReadOnlyAndUnknownAreaPosts() throws Exception {
        JFtnConfig config = config();
        OrmManager orm = new OrmManager("jdbc:h2:mem:echoAccess" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            base.createArea("test");
            Link source = config.getLink(new FTNAddr("2:500/101"));
            subscribe(orm, base, source);
            base.configureSubscription(source, "test", AccessLevel.READ);
            for (String area : new String[]{"TEST", "UNKNOWN"}) {
                Message message = new Message("Author",
                                              "All",
                                              source.getAddr(),
                                              new FTNAddr("2:500/102"),
                                              "Subject",
                                              "AREA:"
                                              + area
                                              + "\r\1MSGID: 2:500/101 "
                                              + area
                                              + "\rHello\r",
                                              new Date());
                Files.write(Path.of(config.getInbound(), area + ".pkt"),
                            PacketWriter.write(source.getAddr(),
                                               new FTNAddr("2:500/102"),
                                               "secret",
                                               Collections.singletonList(message)));
            }
            new Tosser(config, base).runFast(config.getInbound());
            assertEquals(0, base.getMessageSize("test"));
            assertTrue(!base.isAreaExists("unknown"));
            assertTrue(Files.exists(Path.of(config.getTmpDir(), "TEST.bad")));
            assertTrue(Files.exists(Path.of(config.getTmpDir(), "UNKNOWN.bad")));
        } finally {
            base.close();
        }
    }

    @Test
    public void crossZoneExportDoesNotReinterpretOtherZoneSeenByOrPathEntries() throws Exception {
        JFtnConfig config = config("Link=3:600/1,2:500/102,secret,localhost\n");
        OrmManager orm = new OrmManager("jdbc:h2:mem:crossZoneEcho" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            base.createArea("test");
            Link target = config.getLink(new FTNAddr("3:600/1"));
            subscribe(orm, base, target);
            Message message = new Message("Author",
                                          "All",
                                          new FTNAddr("2:400/1"),
                                          new FTNAddr("2:500/102"),
                                          "Subject",
                                          "AREA:TEST\r\1MSGID: 2:400/1 cross-zone\rHello\r"
                                                  + " * Origin: Original (2:400/1)\rSEEN-BY: 500/101\r\1PATH: 500/101\r",
                                          new Date());
            base.addMessage(message, "test");
            new Tosser(config, base).exportEchomail();
            Path file = new OutboundSpool(config).pending(target, true).get(0);
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(file)));
            Message exported = new Message(packet.getMsgs()[0],
                                           packet.getOrigZone(),
                                           packet.getDestZone());
            assertEquals(Collections.singleton(new FTNAddr("3:600/1")),
                         exported.getBody().getSeenBy().getAddresses());
            assertTrue(exported.getBody().getPath().getAddresses().isEmpty());
        } finally {
            base.close();
        }
    }
}
