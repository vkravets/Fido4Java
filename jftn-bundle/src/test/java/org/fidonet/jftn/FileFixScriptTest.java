package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.PartialFileOutputStream;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.db.robots.RobotService;
import org.fidonet.jftn.script.FileDeliveryQueue;
import org.fidonet.jftn.script.MailScripts;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

class FileFixScriptTest {
    @TempDir
    Path root;
    private JFtnConfig config;
    private DatabaseManager base;
    private Tosser tosser;
    private MailScripts scripts;
    private final FTNAddr sender = new FTNAddr("1:500/101");
    private final FTNAddr recipient = new FTNAddr("1:500/102");

    @BeforeEach
    void open() throws Exception {
        String path = root.toString().replace('\\', '/');
        String text = "SysOp=Test\nInbound="
                      + path
                      + "/in\nOutbound="
                      + path
                      + "/out\nTmp="
                      + path
                      + "/state\nEchoPath="
                      + path
                      + "/echo\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\n"
                      + "Link=1:500/102,1:500/100,secret,localhost\n"
                      + "MailScript=classpath:mail-scripts/filefix.groovy\nFileArea=lists|"
                      + path
                      + "/lists\n"
                      + "FileArea=private|"
                      + path
                      + "/private\n"
                      + "FileFixPeer=1:500/102|robot-pass|lists\n"
                      + "TicPeer=1:500/101|in-pass|lists\nTicPeer=1:500/102|out-pass|lists\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        Files.createDirectories(Path.of(config.getTmpDir()));
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:filefix" + System.nanoTime()));
        assertTrue(base.open());
        config.getConfiguredLinks().forEach(link -> base.configureLink(link, "secret"));
        tosser = new Tosser(config, base);
        scripts = new MailScripts(config, base, tosser);
    }

    @AfterEach
    void close() throws Exception {
        if (scripts != null) {
            scripts.close();
        }
        if (base != null) {
            base.close();
        }
    }

    private void command(String text, String id) throws Exception {
        Message message = new Message("Sysop",
                                      "FileFix",
                                      recipient,
                                      new FTNAddr("1:500/100"),
                                      "robot-pass",
                                      text,
                                      new Date());
        message.setMsgId(recipient + " " + id);
        Path inbox = Path.of(config.getInbound());
        Files.createDirectories(inbox);
        Path packet = inbox.resolve("request.pkt");
        Files.write(packet,
                    PacketWriter.write(recipient, message.getTAddr(), "secret", List.of(message)));
        tosser.runFast(inbox.toString(), recipient);
        assertFalse(Files.exists(packet));
    }

    private Path received(String name, String content) throws Exception {
        Path directory = Path.of(config.getInbound(), "peers", ReceivedFile.directoryName(sender));
        Files.createDirectories(directory);
        Path path = Files.createTempFile(directory, "renamed-", ".file");
        Files.writeString(path, content);
        ReceivedFile.record(path, name);
        return path;
    }

    private String tic(String content) {
        CRC32 crc = new CRC32();
        crc.update(content.getBytes(StandardCharsets.UTF_8));
        return "Area LISTS\r\nFile NODELIST.288\r\nFrom 1:500/101\r\nOrigin 1:500/99\r\n"
               + "Crc "
               + String.format("%08X", crc.getValue())
               + "\r\nSize "
               + content.getBytes(StandardCharsets.UTF_8).length
               + "\r\nPw in-pass\r\n"
               + "Path 1:500/101 1700000000\r\nSeenby 1:500/101\r\nX-Future keep this\r\n";
    }

    @Test
    void databaseTicAuthorizationAppliedLiveWithoutUsingFileCredentials() throws Exception {
        scripts.close();
        config.load(new ByteArrayInputStream("RobotAccessSource=database\n".getBytes(StandardCharsets.UTF_8)));
        scripts = new MailScripts(config, base, tosser);
        AtomicInteger delivered = new AtomicInteger();
        scripts.onFile((area, file, name, peer, receipt) -> delivered.incrementAndGet());
        received("NODELIST.288", "hello");
        received("request.tic", tic("hello"));
        scripts.beforeExport(tosser);
        assertEquals(0, delivered.get());
        base.getRobotRules()
            .save(RobotAccessRule.create(RobotService.Tic,
                                         sender.toString(),
                                         "in-pass",
                                         "lists",
                                         "",
                                         true));
        scripts.beforeExport(tosser);
        assertEquals(1, delivered.get());
        base.getRobotRules().delete(RobotService.Tic, sender);
        received("NODELIST.288", "second");
        received("second.tic", tic("second"));
        scripts.beforeExport(tosser);
        assertEquals(1, delivered.get());
    }

    @Test
    void mailServicePublishesReceiveMetadataLoadsFilePairsAndAcknowledgesThem() throws Exception {
        scripts.close();
        scripts = null;
        FileDeliveryQueue queue = new FileDeliveryQueue(config);
        Path source = root.resolve("payload");
        Files.writeString(source, "hello");
        queue.enqueue(config.getLink(recipient), "transport", source, "NODELIST.288", "ticket\r\n");
        try (MailService service = new MailService(config, base)) {
            SessionContext context = JFtnRunner.createSessionContext(config);
            context.getLinksInfo().setCurLink(recipient);
            context.setServerRole(ServerRole.CLIENT);
            service.attach(context);
            context.loadOutbound();
            assertEquals(2, context.getSendFiles().size());
            for (var file : context.getSendFiles()) {
                context.commitSentFile(file);
            }
            assertTrue(queue.pending(config.getLink(recipient)).isEmpty());
            Path partialPath = root.resolve("received.part");
            PartialFileOutputStream partial = new PartialFileOutputStream(partialPath, 5);
            partial.write("hello".getBytes(StandardCharsets.UTF_8));
            partial.close();
            context.commitReceivedFile(new FileData<>(new FileInfo("NODELIST.288", 5, 1), partial));
            assertEquals("hello",
                         Files.readString(config.getReceivedArchivePath()
                                                .resolve(ScriptState.digest("hello"))
                                                .resolve("NODELIST.288")));
            assertTrue(ReceivedFile.list(config)
                                   .stream()
                                   .anyMatch(file -> file.getName().equals("NODELIST.288")
                                           && file.getPeer().equals(recipient)));
            context.closeReceiveStreams();
        }
    }

    @Test
    void subscribesPersistsAcrossRestartAndForwardsValidatedFilesInTransportOrder()
            throws Exception {
        command("+*\r%QUERY\r", "subscribe");
        String reply = base.getQueuedNetmail().get(0).getMessage().getBody().getBody();
        assertTrue(reply.contains("lists"));
        assertFalse(reply.contains("private"));
        scripts.close();
        scripts = new MailScripts(config, base, tosser);
        AtomicInteger delivered = new AtomicInteger();
        scripts.onFile((area, file, name, peer, receipt) -> {
            assertEquals("lists", area);
            assertEquals("NODELIST.288", name);
            assertEquals("hello", Files.readString(file));
            delivered.incrementAndGet();
        });
        received("NODELIST.288", "hello");
        received("abcdefgh.tic", tic("hello"));
        scripts.beforeExport(tosser);
        assertEquals(1, delivered.get());
        FileDeliveryQueue queue = new FileDeliveryQueue(config);
        List<Path> pending = queue.pending(config.getLink(recipient));
        assertEquals(2, pending.size());
        assertEquals("NODELIST.288", pending.get(0).getFileName().toString());
        String outgoing = Files.readString(pending.get(1));
        assertTrue(outgoing.contains("Pw out-pass\r\n"));
        assertFalse(outgoing.contains("in-pass"));
        assertTrue(outgoing.contains("X-Future keep this"));
        queue.acknowledge(pending.get(0));
        assertEquals(1, queue.pending(config.getLink(recipient)).size());
        queue.acknowledge(pending.get(1));
        assertTrue(queue.pending(config.getLink(recipient)).isEmpty());
        scripts.beforeExport(tosser);
        assertEquals(1, delivered.get());
        assertTrue(queue.pending(config.getLink(recipient)).isEmpty());
    }

    @Test
    void defersMissingPayloadAndConsumerFailuresWithoutLosingTheIncomingFiles() throws Exception {
        received("abcdefgh.tic", tic("hello"));
        scripts.beforeExport(tosser);
        assertFalse(Files.exists(root.resolve("lists")));
        Path payload = received("NODELIST.288", "hello");
        AtomicInteger attempts = new AtomicInteger();
        scripts.onFile((area, file, name, peer, receipt) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("consumer retry");
            }
        });
        scripts.beforeExport(tosser);
        scripts.beforeExport(tosser);
        assertEquals(2, attempts.get());
        assertTrue(Files.exists(payload));
    }

    @Test
    void rejectsBadPasswordsPathTraversalCrcMismatchLoopsAndUnsubscribedForwarding()
            throws Exception {
        received("NODELIST.288", "hello");
        received("badpass.tic", tic("hello").replace("Pw in-pass", "Pw incorrect"));
        received("badpath.tic", tic("hello").replace("File NODELIST.288", "File ../escape"));
        received("badcrc.tic", tic("wrong"));
        received("loop.tic", tic("hello") + "Path 1:500/100 1700000001\r\n");
        scripts.beforeExport(tosser);
        assertFalse(Files.exists(root.resolve("lists")));
        assertTrue(new FileDeliveryQueue(config).pending(config.getLink(recipient)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ReceivedFile.safeName("../escape"));
        received("valid.tic", tic("hello"));
        scripts.beforeExport(tosser);
        assertTrue(Files.isDirectory(root.resolve("lists")));
        assertTrue(new FileDeliveryQueue(config).pending(config.getLink(recipient)).isEmpty());
    }
}
