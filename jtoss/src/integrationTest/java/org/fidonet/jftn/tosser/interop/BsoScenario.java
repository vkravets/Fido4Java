package org.fidonet.jftn.tosser.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.BsoOutbound;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Short-lived production workers deliberately reopen SQL and spool state between phases. */
public final class BsoScenario implements AutoCloseable {
    private static final FTNAddr LOCAL = new FTNAddr("1:500/100");
    final Path root;
    final FTNAddr remote;
    final JFtnConfig config;
    final MailDatabaseFacade base;
    final Tosser tosser;
    private final String flavour;

    BsoScenario(Path root, String address, String flavour) throws Exception {
        this.root = root;
        this.remote = new FTNAddr(address);
        this.flavour = flavour;
        Files.createDirectories(root.resolve("attachments"));
        String text = "SysOp=Test\nInbound="
                      + root.resolve("inbound")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nLink="
                      + remote
                      + ","
                      + LOCAL
                      + ",session-secret,receiver\nPacketPassword="
                      + remote
                      + ",pktpass1"
                      + "\nBsoEnabled=true\nBsoOutbound="
                      + root.resolve("bso")
                      + "\nFileAttachRoot="
                      + root.resolve("attachments")
                      + "\nRouteFile=*,"
                      + remote
                      + "\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:" + root.resolve("mail")));
        assertTrue(base.open());
        Link link = config.getLink(remote);
        base.configureLink(link, "pktpass1");
        base.configureSubscription(link, "interop", AccessLevel.BOTH);
        tosser = new Tosser(config, base);
    }

    public static void main(String[] args) throws Exception {
        try (BsoScenario scenario = new BsoScenario(Path.of(args[0]), args[2], args[3])) {
            scenario.execute(args[1]);
        }
        System.out.println("Verified BSO " + args[1]);
    }

    private void execute(String command) throws Exception {
        switch (command) {
            case "prepare-batch" :
                prepareBatch();
                break;
            case "prepare-one" :
                tosser.submitNetmail(message("Normal mail", "Body\r", 1), "normal");
                export();
                assertTrue(Files.isRegularFile(new BsoOutbound(config).control(remote,
                                                                               extension())));
                break;
            case "verify-queued" :
                export();
                assertEquals(1, base.getQueuedNetmail().size());
                break;
            case "verify-actions-pending" :
                export();
                assertEquals(3, base.getQueuedNetmail().size());
                assertEquals("KFS original", Files.readString(root.resolve("attachments/KFS.ZIP")));
                assertEquals("TFS original", Files.readString(root.resolve("attachments/TFS.LOG")));
                break;
            case "verify-sent" :
                export();
                assertTrue(base.getQueuedNetmail().isEmpty());
                break;
            case "verify-actions-sent" :
                export();
                assertTrue(base.getQueuedNetmail().isEmpty());
                assertFalse(Files.exists(root.resolve("attachments/KFS.ZIP")));
                assertEquals(0, Files.size(root.resolve("attachments/TFS.LOG")));
                assertEquals(2, originals().size());
                break;
            case "verify-one-received" :
                verifyPackets(1);
                break;
            case "verify-batch-received" :
                verifyPackets(4);
                assertEquals("KFS original", Files.readString(root.resolve("inbound/KFS.ZIP")));
                assertEquals("TFS original", Files.readString(root.resolve("inbound/TFS.LOG")));
                break;
            case "verify-empty" :
                assertTrue(packets().isEmpty());
                break;
            case "prepare-busy" :
                Files.writeString(new BsoOutbound(config).control(remote, "bsy"), "test-held");
                Files.writeString(root.resolve("BLOCKED.ZIP"), "Existing mailer job");
                Files.writeString(new BsoOutbound(config).control(remote, "flo"),
                                  "^" + root.resolve("BLOCKED.ZIP") + "\r\n");
                tosser.submitNetmail(message("Normal mail", "Body\r", 1), "normal");
                assertThrows(IOException.class, this::export);
                break;
            case "verify-busy" :
                assertEquals("test-held",
                             Files.readString(new BsoOutbound(config).control(remote, "bsy")));
                assertEquals(1, base.getQueuedNetmail().size());
                assertEquals("^" + root.resolve("BLOCKED.ZIP") + "\r\n",
                             Files.readString(new BsoOutbound(config).control(remote, "flo")));
                assertTrue(Files.isRegularFile(root.resolve("BLOCKED.ZIP")));
                break;
            case "release-busy" :
                Files.delete(new BsoOutbound(config).control(remote, "bsy"));
                export();
                break;
            case "verify-busy-delivered" :
                verifyPackets(1);
                assertEquals("Existing mailer job",
                             Files.readString(root.resolve("inbound/BLOCKED.ZIP")));
                break;
            default :
                throw new IllegalArgumentException("Unknown BSO command " + command);
        }
    }

    private void prepareBatch() throws Exception {
        Files.writeString(root.resolve("attachments/KFS.ZIP"), "KFS original");
        Files.writeString(root.resolve("attachments/TFS.LOG"), "TFS original");
        tosser.submitNetmail(message("Normal mail", "Body\r", 1), "normal");
        tosser.submitNetmail(message("KFS.ZIP", "\1FLAGS: KFS\rBody\r", 17), "kfs");
        tosser.submitNetmail(message("TFS.LOG", "\1FLAGS: TFS\rBody\r", 17), "tfs");
        tosser.submitEchomail(message("Echo mail", "AREA:INTEROP\rEcho body\r", 0));
        export();
    }

    Message message(String subject, String body, int attributes) {
        String flags = flavour.equals("normal") ? "" : "\1FLAGS: " + flag() + "\r";
        Message message = new Message("Author",
                                      "Reader",
                                      LOCAL,
                                      remote,
                                      subject,
                                      flags + body,
                                      new Date());
        message.setAttributes(attributes);
        return message;
    }

    private String flag() {
        switch (flavour) {
            case "crash" :
                return "CRA";
            case "hold" :
                return "HLD";
            case "direct" :
                return "DIR";
            case "immediate" :
                return "IMM";
            default :
                throw new IllegalArgumentException("Invalid flavour");
        }
    }

    private String extension() {
        switch (flavour) {
            case "normal" :
                return "flo";
            case "crash" :
                return "clo";
            case "hold" :
                return "hlo";
            case "direct" :
                return "dlo";
            case "immediate" :
                return "ilo";
            default :
                throw new IllegalArgumentException("Invalid flavour");
        }
    }

    void export() throws Exception {
        tosser.exportPendingNetmail();
        tosser.exportEchomail();
    }

    private List<Path> packets() throws Exception {
        try (Stream<Path> files = Files.list(root.resolve("inbound"))) {
            return files.filter(path -> path.getFileName()
                                            .toString()
                                            .toLowerCase()
                                            .endsWith(".pkt"))
                        .collect(Collectors.toList());
        }
    }

    private List<Path> originals() throws Exception {
        try (Stream<Path> files = Files.walk(root.resolve("tmp/bso-journal"))) {
            return files.filter(path -> Files.isRegularFile(path)
                    && path.getParent().getFileName().toString().equals("originals"))
                        .collect(Collectors.toList());
        }
    }

    private void verifyPackets(int count) throws Exception {
        assertEquals(count, packets().size());
        Set<String> subjects = new HashSet<>();
        for (Path path : packets()) {
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(path)));
            assertEquals(LOCAL, packet.getOrigaddr());
            assertEquals(remote,
                         new FTNAddr(packet.getDestZone(),
                                     packet.getDestNet(),
                                     packet.getDestNode(),
                                     packet.getDestPoint()));
            assertEquals(1, packet.getMsgs().length);
            assertEquals("pktpass1", packet.getPass());
            Message message = new Message(packet.getMsgs()[0],
                                          packet.getOrigZone(),
                                          packet.getDestZone());
            subjects.add(message.getSubject());
            assertTrue(message.getMsgId().startsWith(LOCAL.toString().replace(".0", "")));
            assertEquals(0, message.getAttributes() & (0x20 | 0x80 | 0x100));
            assertTrue(message.getBody()
                              .toString()
                              .contains(message.isEchomail() ? "Echo body" : "Body"));
        }
        assertEquals(count == 4
                ? Set.of("Normal mail", "KFS.ZIP", "TFS.LOG", "Echo mail")
                : Set.of("Normal mail"), subjects);
    }

    @Override
    public void close() {
        base.close();
    }
}
