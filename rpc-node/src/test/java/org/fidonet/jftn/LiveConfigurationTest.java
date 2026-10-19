package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.ProfileHandler;
import org.fidonet.jftn.rpc.auth.RpcAccounts;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

class LiveConfigurationTest {
    @TempDir
    Path root;
    private JFtnConfig config;
    private OrmManager orm;
    private MailDatabaseFacade base;
    private MailRuntime mail;
    private PollService polls;
    private RpcAccounts accounts;
    private LiveConfiguration reload;
    private Path source;
    private String text;

    @BeforeEach
    void open() throws Exception {
        String path = root.toString().replace('\\', '/');
        text = "SysOp=Original\nInbound="
               + path
               + "/in\nOutbound="
               + path
               + "/out\nTmp="
               + path
               + "/state\nEchoPath="
               + path
               + "/echo\n"
               + "Link=1:500/101,1:500/100,secret,localhost\n"
               + "JamBridgeEnabled=false\nTossIntervalSeconds=86400\n";
        source = root.resolve("jftn.conf");
        Files.writeString(source, text);
        config = new JFtnConfig();
        config.load(source.toString());
        orm = new OrmManager("jdbc:h2:mem:reload" + System.nanoTime());
        base = new MailDatabaseFacade(orm);
        assertTrue(base.open());
        mail = new MailRuntime(config, base, List.of());
        SessionContext template = NodeSessions.create(config);
        mail.attach(template);
        polls = new PollService(config, template);
        accounts = new RpcAccounts(root.resolve("accounts"));
        ProfileHandler profiles = new ProfileHandler(new AccountDirectory(accounts),
                                                     new UiSessions(accounts, 3600),
                                                     List.of("1:500/100.0"),
                                                     false);
        reload = new LiveConfiguration(config, base, mail, polls, template, profiles);
    }

    @AfterEach
    void close() throws Exception {
        if (polls != null)
            polls.close();
        if (mail != null)
            mail.close();
        if (accounts != null)
            accounts.close();
        if (base != null)
            base.close();
    }

    @Test
    void rolledBackSqlAndKeptLiveSnapshotWhenPublicationFailed() throws Exception {
        mail.start();
        byte[] next = text.replace(",secret,", ",updated,")
                          .replace("TossIntervalSeconds=86400", "TossIntervalSeconds=1")
                          .getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> reload.apply(JFtnConfig.candidate(source, next), () -> {
            throw new IOException("Disk publication failed");
        }));
        assertEquals("secret", config.getLink(new FTNAddr("1:500/101")).getPass());
        assertEquals("86400", config.getValue("TossIntervalSeconds"));
        assertEquals("secret",
                     orm.getDao(ConfigurationLink.class).queryForAll().get(0).getPassword());
        assertEquals(text, Files.readString(source));
    }

    @Test
    void replacedDayLongTimerAndProcessedMailWithNewInterval() throws Exception {
        mail.start();
        Path packet = root.resolve("in/probe.pkt");
        Files.write(packet, emptyPacket());
        byte[] next = text.replace("TossIntervalSeconds=86400", "TossIntervalSeconds=1")
                          .getBytes(StandardCharsets.UTF_8);
        reload.apply(JFtnConfig.candidate(source, next), () -> Files.write(source, next));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Files.exists(packet) && System.nanoTime() < deadline)
            TimeUnit.MILLISECONDS.sleep(20);
        assertFalse(Files.exists(packet),
                    "Reloaded timer must process inbound mail without a restart");
        assertEquals("1", config.getValue("TossIntervalSeconds"));
    }

    private byte[] emptyPacket() {
        ByteBuffer packet = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort(0, (short) 101);
        packet.putShort(2, (short) 100);
        packet.putShort(18, (short) 2);
        packet.putShort(20, (short) 500);
        packet.putShort(22, (short) 500);
        packet.putShort(34, (short) 1);
        packet.putShort(36, (short) 1);
        packet.position(26);
        packet.put("secret".getBytes(StandardCharsets.US_ASCII));
        return packet.array();
    }
}
