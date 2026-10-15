package org.fidonet.jftn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.PartialFileOutputStream;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.binkp.netty.plugin.Server;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;

public class MailServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private JFtnConfig config(String own, String peer) throws Exception {
        return config(own, peer, "");
    }

    private JFtnConfig config(String own, String peer, String extra) throws Exception {
        String root = temporary.newFolder().getAbsolutePath().replace('\\', '/');
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
                      + "Link="
                      + peer
                      + ","
                      + own
                      + ",secret,127.0.0.1\n"
                      + "EchoArea=TEST,"
                      + peer
                      + "\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream((text + extra).getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private DatabaseManager database() {
        DatabaseManager database = new DatabaseManager(new OrmManager("jdbc:h2:mem:mailService"
                                                                      + System.nanoTime()));
        assertTrue(database.open());
        return database;
    }

    private Message message(String from, String to, boolean echo) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr(from),
                           new FTNAddr(to),
                           "Subject",
                           (echo ? "AREA:TEST\r" : "") + "\1CHRS: UTF-8 4\rТекст сообщения\r",
                           new Date());
    }

    @Test(timeout = 60000)
    public void deliversNetmailAndEchomailInBothDirectionsThroughRealTcp() throws Exception {
        JFtnConfig clientConfig = config("2:500/101", "2:500/102");
        JFtnConfig serverConfig = config("2:500/102", "2:500/101");
        DatabaseManager clientBase = database();
        DatabaseManager serverBase = database();
        Server server = new Server(0);
        Client client = null;
        try (MailService clientMail = new MailService(clientConfig, clientBase);
                MailService serverMail = new MailService(serverConfig, serverBase)) {
            clientMail.getTosser()
                      .submitNetmail(message("2:500/101", "2:500/102", false), "client-local");
            serverMail.getTosser()
                      .submitNetmail(message("2:500/102", "2:500/101", false), "server-local");
            clientMail.getTosser().submitEchomail(message("2:500/101", "2:500/102", true));
            serverMail.getTosser().submitEchomail(message("2:500/102", "2:500/101", true));
            SessionContext clientContext = JFtnRunner.createSessionContext(clientConfig);
            SessionContext serverContext = JFtnRunner.createSessionContext(serverConfig);
            clientMail.attach(clientContext);
            serverMail.attach(serverContext);
            clientMail.start();
            serverMail.start();
            server.run(serverContext);
            Link destination = new Link(new FTNAddr("2:500/102"),
                                        Collections.singletonList(new FTNAddr("2:500/101")),
                                        "secret",
                                        "127.0.0.1",
                                        server.getLocalPort());
            client = new Client(destination);
            client.run(clientContext);
            assertTrue("Messages and acknowledgements must reach both databases",
                       awaitDelivery(clientBase, serverBase, clientConfig, serverConfig));
            assertEquals("Текст сообщения\r",
                         clientBase.getLocalNetmail(new FTNAddr("2:500/101"))
                                   .get(0)
                                   .getBody()
                                   .getBody());
            assertEquals(2, clientBase.getMessageSize("test"));
            assertEquals(2, serverBase.getMessageSize("test"));
            assertTrue(new OutboundSpool(clientConfig).pending(clientConfig.getConfiguredLinks()
                                                                           .get(0),
                                                               true)
                                                      .isEmpty());
            assertTrue(new OutboundSpool(serverConfig).pending(serverConfig.getConfiguredLinks()
                                                                           .get(0),
                                                               true)
                                                      .isEmpty());
        } finally {
            if (client != null) {
                client.stop();
            }
            server.stop();
            clientBase.close();
            serverBase.close();
        }
    }

    private boolean delivered(DatabaseManager client, DatabaseManager server) {
        return client.getLocalNetmail(new FTNAddr("2:500/101")).size() == 1
                && server.getLocalNetmail(new FTNAddr("2:500/102")).size() == 1
                && client.getMessageSize("test") == 2 && server.getMessageSize("test") == 2
                && client.getQueuedNetmail().isEmpty() && server.getQueuedNetmail().isEmpty();
    }

    private boolean awaitDelivery(DatabaseManager client,
                                  DatabaseManager server,
                                  JFtnConfig clientConfig,
                                  JFtnConfig serverConfig)
            throws Exception {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (delivered(client, server) && outboundEmpty(clientConfig)
                    && outboundEmpty(serverConfig)) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private boolean outboundEmpty(JFtnConfig config) throws IOException {
        return new OutboundSpool(config).pending(config.getConfiguredLinks().get(0), true)
                                        .isEmpty();
    }

    @Test
    public void rejectsPacketsImpersonatingAnotherConfiguredPeer() throws Exception {
        JFtnConfig config = config("2:500/102",
                                   "2:500/101",
                                   "Link=2:500/103,2:500/102,secret,127.0.0.1\n");
        DatabaseManager base = database();
        try (MailService service = new MailService(config, base)) {
            SessionContext context = JFtnRunner.createSessionContext(config);
            context.getLinksInfo().setCurLink(new FTNAddr("2:500/101"));
            service.attach(context);
            byte[] bytes = PacketWriter.write(new FTNAddr("2:500/103"),
                                              new FTNAddr("2:500/102"),
                                              "secret",
                                              Collections.singletonList(message("2:500/103",
                                                                                "2:500/102",
                                                                                false)));
            PartialFileOutputStream partial = new PartialFileOutputStream(Path.of(config.getTmpDir(),
                                                                                  "forged.part"),
                                                                          bytes.length);
            partial.write(bytes);
            partial.close();
            context.commitReceivedFile(new FileData<>(new FileInfo("forged.pkt", bytes.length, 1),
                                                      partial));
            service.processPending();
            assertTrue(base.getLocalNetmail(new FTNAddr("2:500/102")).isEmpty());
            assertTrue(Files.exists(Path.of(config.getTmpDir(), "forged.bad")));
        } finally {
            base.close();
        }
    }

    @Test
    public void recoversExistingInboundAndPreventsConcurrentServiceInstances() throws Exception {
        JFtnConfig config = config("2:500/102", "2:500/101");
        DatabaseManager base = database();
        try {
            Files.createDirectories(Path.of(config.getInbound()));
            byte[] packet = PacketWriter.write(new FTNAddr("2:500/101"),
                                               new FTNAddr("2:500/102"),
                                               "secret",
                                               Collections.singletonList(message("2:500/101",
                                                                                 "2:500/102",
                                                                                 false)));
            Files.write(Path.of(config.getInbound(), "recovered.pkt"), packet);
            try (MailService service = new MailService(config, base)) {
                try {
                    new MailService(config, base).close();
                    fail("A second service must not acquire the same state directory");
                } catch (IOException expected) {
                    assertTrue(expected.getMessage().contains("already using"));
                }
                service.start();
                assertEquals(1, base.getLocalNetmail(new FTNAddr("2:500/102")).size());
                assertFalse(Files.exists(Path.of(config.getInbound(), "recovered.pkt")));
            }
            try (MailService reopened = new MailService(config, base)) {
                reopened.start();
                assertEquals(1, base.getLocalNetmail(new FTNAddr("2:500/102")).size());
            }
        } finally {
            base.close();
        }
    }

    @Test
    public void publishesOnlyCompletedFilesAndReleasesUnacknowledgedPacketsOnDisconnect()
            throws Exception {
        JFtnConfig config = config("2:500/102", "2:500/101");
        DatabaseManager base = database();
        try (MailService service = new MailService(config, base)) {
            SessionContext context = JFtnRunner.createSessionContext(config);
            context.getLinksInfo().setCurLink(new FTNAddr("2:500/101"));
            context.setServerRole(ServerRole.CLIENT);
            service.attach(context);
            service.getTosser().submitNetmail(message("2:500/102", "2:500/101", false), "retry");
            context.loadOutbound();
            assertEquals(1, context.getSendFiles().size());
            context.closeReceiveStreams();
            SessionContext next = new SessionContext(context);
            next.setServerRole(ServerRole.CLIENT);
            next.loadOutbound();
            assertEquals(1, next.getSendFiles().size());
            Path partialPath = Path.of(config.getTmpDir(), "published.part");
            PartialFileOutputStream partial = new PartialFileOutputStream(partialPath, 3);
            partial.write(new byte[]{1, 2, 3});
            partial.close();
            FileData<OutputStream> file = new FileData<>(new FileInfo("sample.pkt", 3, 1), partial);
            next.commitReceivedFile(file);
            assertFalse(Files.exists(partialPath));
            assertTrue(Files.exists(partial.getPath()));
            assertTrue(partial.getPath().startsWith(Path.of(config.getInbound())));
            assertEquals(3, partial.toByteArray().length);
            next.closeReceiveStreams();
        } finally {
            base.close();
        }
    }
}
