package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.QueuedNetmail;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.script.RemoteRobotRequests;
import org.fidonet.jftn.script.RobotRequest;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;

class RemoteRobotRequestTest {
    @TempDir
    Path root;
    private JFtnConfig config;
    private MailDatabaseFacade base;
    private Tosser tosser;
    private String text;
    private final FTNAddr peer = new FTNAddr("1:500/101");
    private final FTNAddr local = new FTNAddr("1:500/100");

    @BeforeEach
    void open() throws Exception {
        String path = root.toString().replace('\\', '/');
        text = "SysOp=Test\nInbound="
               + path
               + "/in\nOutbound="
               + path
               + "/out\nTmp="
               + path
               + "/tmp\nEchoPath="
               + path
               + "/echo\n"
               + "Link=1:500/101,1:500/100,session-pass,localhost\n"
               + "PacketPassword=1:500/101,pkt-pass\nDefaultRoute=1:500/101\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:remoteRobots"
                                                     + System.nanoTime()));
        assertTrue(base.open());
        tosser = new Tosser(config, base);
    }

    @AfterEach
    void close() {
        base.close();
    }

    private RemoteRobotRequests requests() throws IOException {
        return new RemoteRobotRequests(config,
                                       tosser,
                                       new ScriptState(Path.of(config.getTmpDir(), "scripts")));
    }

    @Test
    void queuesBothRobotEnvelopesAndExportsCommandsThroughOrdinaryPkt() throws Exception {
        requests().submit("areafix",
                          peer,
                          local,
                          "area-secret",
                          List.of("%LIST", "+TEST.ONE"),
                          "area-1");
        requests().submit("filefix",
                          peer,
                          local,
                          "file-secret",
                          List.of("%RESEND NODELIST.288 LISTS"),
                          "file-1");
        List<QueuedNetmail> queued = base.getQueuedNetmail();
        assertEquals(2, queued.size());
        Message area = queued.stream()
                             .map(QueuedNetmail::getMessage)
                             .filter(message -> message.getTo().equals("AreaFix"))
                             .findFirst()
                             .orElseThrow();
        Message file = queued.stream()
                             .map(QueuedNetmail::getMessage)
                             .filter(message -> message.getTo().equals("FileFix"))
                             .findFirst()
                             .orElseThrow();
        assertEquals("area-secret", area.getSubject());
        assertEquals("%LIST\r+TEST.ONE\r", area.getBody().getBody());
        assertEquals(1, file.getAttributes() & 1);
        tosser.exportPendingNetmail();
        List<Path> packets = new OutboundSpool(config).pending(config.getLink(peer), true);
        assertEquals(2, packets.size());
        for (Path path : packets) {
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(path)));
            Message message = new Message(packet.getMsgs()[0],
                                          packet.getOrigZone(),
                                          packet.getDestZone());
            assertEquals(peer, message.getTAddr());
            assertEquals(local, message.getFAddr());
            assertTrue(message.getMsgId().matches(".* [0-9a-f]{8}"));
        }
    }

    @Test
    void stableRequestIdsSurviveRestartAndRejectChangedCommands() throws Exception {
        String key = requests().submit("AreaFix",
                                       peer,
                                       local,
                                       "secret",
                                       List.of("%QUERY"),
                                       "stable");
        assertEquals(key,
                     requests().submit("AreaFix",
                                       peer,
                                       local,
                                       "secret",
                                       List.of("%QUERY"),
                                       "stable"));
        assertEquals(1, base.getQueuedNetmail().size());
        assertThrows(IllegalArgumentException.class,
                     () -> requests().submit("AreaFix",
                                             peer,
                                             local,
                                             "secret",
                                             List.of("-*"),
                                             "stable"));
        assertEquals(1, base.getQueuedNetmail().size());
    }

    @Test
    void rejectsUnknownPeersForgedAuthorsInvalidServicesAndLineInjection() throws Exception {
        assertThrows(IllegalArgumentException.class,
                     () -> requests().submit("AreaFix",
                                             new FTNAddr("1:500/999"),
                                             local,
                                             "secret",
                                             List.of("%LIST"),
                                             "x"));
        assertThrows(IllegalArgumentException.class,
                     () -> requests().submit("AreaFix",
                                             peer,
                                             new FTNAddr("1:500/999"),
                                             "secret",
                                             List.of("%LIST"),
                                             "x"));
        assertThrows(IllegalArgumentException.class,
                     () -> requests().submit("Tic", peer, local, "secret", List.of("%LIST"), "x"));
        assertThrows(IllegalArgumentException.class,
                     () -> requests().submit("AreaFix",
                                             peer,
                                             local,
                                             "secret",
                                             List.of("%LIST\r-*"),
                                             "x"));
        assertThrows(IllegalArgumentException.class,
                     () -> requests().submit("AreaFix",
                                             peer,
                                             local,
                                             "secret\n",
                                             List.of("%LIST"),
                                             "x"));
        assertTrue(base.getQueuedNetmail().isEmpty());
    }

    @Test
    void privateRequestFileFieldsUseTheSameValidationAndQueue() throws Exception {
        Properties request = new Properties();
        request.setProperty("Robot", "FileFix");
        request.setProperty("To", peer.toString());
        request.setProperty("From", local.toString());
        request.setProperty("Password", "remote-file-password");
        request.setProperty("Commands", "%LIST\n+LISTS");
        request.setProperty("RequestId", "console-request");
        RobotRequestCommand.submit(config, tosser, request);
        assertEquals(1, base.getQueuedNetmail().size());
        assertEquals("%LIST\r+LISTS\r",
                     base.getQueuedNetmail().get(0).getMessage().getBody().getBody());
        assertFalse(RobotRequestCommand.handle(new String[]{"--version"}));
        Files.createDirectories(Path.of(config.getTmpDir()));
        Path configuration = root.resolve("jftn.conf");
        Files.writeString(configuration, text);
        try (FileChannel channel = FileChannel.open(Path.of(config.getTmpDir(),
                                                            "mail-service.lock"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            assertThrows(IOException.class,
                         () -> RobotRequestCommand.handle(new String[]{"--robot-request",
                                 "unused-private-file", configuration.toString()}));
        }
    }

    @Test
    void parserHonorsLineLimitsAndTearLinesWithoutExecutingControlLines() {
        assertEquals(List.of("%LIST"), RobotRequest.commands("\1MSGID: ignored\r%LIST\r---\r-*"));
        assertEquals(200, RobotRequest.commands("%LIST\n".repeat(200)).size());
        assertThrows(IllegalArgumentException.class,
                     () -> RobotRequest.commands("%LIST\n".repeat(201)));
        assertThrows(IllegalArgumentException.class,
                     () -> RobotRequest.commands("x".repeat(64001)));
    }
}
