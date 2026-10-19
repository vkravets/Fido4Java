package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.FileEchoEvent;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Production plugin worker; its package grants access to area administration without RPC/UI. */
public final class HtickScenario implements AutoCloseable {
    private static final FTNAddr PEER = new FTNAddr("1:500/101");
    private static final String JAVA_TEXT = "Payload produced by JFtn\r\n";
    private static final String HTICK_TEXT = "Payload hatched by HTick\r\n";
    private final Path root;
    private final Path area;
    private final MailDatabaseFacade base;
    private final MailAutomationRuntime scripts;
    private final Tosser tosser;
    private final List<FileEchoEvent> events = new ArrayList<>();

    private HtickScenario(Path root) throws Exception {
        this.root = root;
        area = root.resolve("jftn-files/interop");
        Files.createDirectories(root.resolve("htick-pass"));
        for (String directory : List.of("htick-tmp-in",
                                        "htick-tmp-out",
                                        "htick-dupes",
                                        "jam",
                                        "htick-files/interop",
                                        "inbound")) {
            Files.createDirectories(root.resolve(directory));
        }
        String text = "SysOp=Test\nInbound=" + root.resolve("inbound") + "\nOutbound="
                + root.resolve("out") + "\nTmp=" + root.resolve("tmp") + "\nEchoPath="
                + root.resolve("echo")
                + "\nLink=1:500/101,1:500/100,session-secret,receiver\nPacketPassword=1:500/101,pktpass1"
                + "\nBsoEnabled=true\nBsoOutbound=" + root.resolve("bso")
                + "\nRobotAccessSource=files\nTicPeer=1:500/101|tic-secret|interop"
                + "\nFileFixPeer=1:500/101|fix-secret|interop\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:" + root.resolve("mail")));
        assertTrue(base.open());
        tosser = new Tosser(config, base);
        scripts = new MailAutomationRuntime(config, base, tosser, List.of(new FileEchoPlugin()));
        FileEchoAreaService store = ((FileEchoProcessor) scripts.getFileEcho()).store();
        store.saveArea("interop", area.toString(), "Interop files", true);
        store.saveAccess("interop", PEER.toString(), "BOTH");
        scripts.onFileEcho(events::add);
    }

    public static void main(String[] args) throws Exception {
        try (HtickScenario scenario = new HtickScenario(Path.of(args[0]))) {
            scenario.execute(args[1]);
        }
        System.out.println("Verified HTick " + args[1]);
    }

    private void execute(String command) throws Exception {
        switch (command) {
            case "init" :
                Files.writeString(root.resolve("UPDATE.ZIP"), HTICK_TEXT);
                break;
            case "prepare" :
                Files.createDirectories(area);
                Path payload = Files.writeString(area.resolve("JAVA.ZIP"), JAVA_TEXT);
                Files.setLastModifiedTime(payload,
                                          FileTime.from(Instant.ofEpochSecond(1700000000)));
                scripts.getFileEcho().resend(PEER, "interop", "JAVA.ZIP", "java-to-htick");
                scripts.beforeExport(tosser);
                break;
            case "verify-htick" :
                assertEquals(JAVA_TEXT,
                             Files.readString(root.resolve("htick-files/interop/JAVA.ZIP")));
                assertTrue(Files.readString(root.resolve("htick-files/interop/files.bbs"))
                                .contains("Requested file resend"));
                break;
            case "receive" :
                adoptInbound();
                scripts.beforeExport(tosser);
                assertEquals(1, events.size());
                FileEchoEvent event = events.get(0);
                assertEquals("accepted", event.status);
                assertEquals("UPDATE.ZIP", event.name);
                assertTrue(event.descriptions.contains("HTick description"));
                assertEquals(PEER, event.peer);
                assertEquals(HTICK_TEXT.getBytes(StandardCharsets.UTF_8).length, event.size);
                verifyImported();
                break;
            case "verify-imported" :
                scripts.beforeExport(tosser);
                assertTrue(events.isEmpty());
                verifyImported();
                break;
            case "bad-password" :
            case "bad-crc" :
            case "bad-area" :
                rejectAndRetain(command);
                break;
            case "repair" :
                restoreInbound();
                scripts.beforeExport(tosser);
                assertEquals(1, events.size());
                verifyImported();
                break;
            default :
                throw new IllegalArgumentException("Unknown HTick command " + command);
        }
    }

    private void verifyImported() throws Exception {
        assertEquals(HTICK_TEXT, Files.readString(area.resolve("UPDATE.ZIP")));
        assertFalse(Files.exists(area.resolve("JAVA.ZIP")));
        List<Path> retained;
        try (Stream<Path> files = Files.walk(area.resolve("dupes"))) {
            retained = files.filter(path -> path.getFileName().toString().equals("JAVA.ZIP"))
                            .collect(Collectors.toList());
        }
        assertEquals(1, retained.size());
        assertEquals(JAVA_TEXT, Files.readString(retained.get(0)));
    }

    private List<Path> inbound() throws Exception {
        try (Stream<Path> files = Files.list(root.resolve("inbound"))) {
            return files.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    private Path peerDirectory() {
        return root.resolve("inbound/peers/1-500-101-0");
    }

    private void adoptInbound() throws Exception {
        Files.createDirectories(peerDirectory());
        for (Path original : inbound()) {
            Path copy = peerDirectory().resolve(original.getFileName());
            if (!Files.exists(copy)) {
                Files.copy(original, copy);
                ReceivedFile.record(copy, original.getFileName().toString());
            }
        }
    }

    private void restoreInbound() throws Exception {
        for (Path original : inbound()) {
            Files.copy(original,
                       peerDirectory().resolve(original.getFileName()),
                       StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rejectAndRetain(String problem) throws Exception {
        adoptInbound();
        Path ticket;
        try (Stream<Path> files = Files.list(peerDirectory())) {
            ticket = files.filter(path -> path.getFileName()
                                              .toString()
                                              .toLowerCase()
                                              .endsWith(".tic"))
                          .findFirst()
                          .orElseThrow();
        }
        String text = Files.readString(ticket);
        if (problem.equals("bad-password")) {
            Files.writeString(ticket, text.replaceAll("(?im)^Pw .*$", "Pw wrong-password"));
        } else if (problem.equals("bad-area")) {
            Files.writeString(ticket, text.replaceAll("(?im)^Area .*$", "Area forbidden"));
        } else {
            Files.writeString(peerDirectory().resolve("UPDATE.ZIP"), "damaged");
        }
        scripts.beforeExport(tosser);
        assertEquals(1, events.size());
        assertEquals("error", events.get(0).status);
        assertFalse(events.get(0).detail.isBlank());
        assertFalse(events.get(0).detail.contains("tic-secret"));
        assertTrue(Files.isRegularFile(ticket));
        assertFalse(Files.exists(area.resolve("UPDATE.ZIP")));
        assertEquals(JAVA_TEXT, Files.readString(area.resolve("JAVA.ZIP")));
    }

    @Override
    public void close() throws Exception {
        try {
            scripts.close();
        } finally {
            base.close();
        }
    }
}
