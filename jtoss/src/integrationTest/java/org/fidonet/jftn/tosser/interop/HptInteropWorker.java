package org.fidonet.jftn.tosser.interop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.echobase.jam.JamArea;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.jam.JamArchiveImporter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Command boundary used by the host test. Assertions execute against production classes. */
public final class HptInteropWorker {
    private HptInteropWorker() {
    }

    public static void main(String[] arguments) throws Exception {
        try (HptScenario scenario = new HptScenario(Path.of(arguments[1]))) {
            execute(arguments[0], scenario);
        }
    }

    private static void execute(String command, HptScenario scenario) throws Exception {
        switch (command) {
            case "init" :
                break;
            case "export-java" :
                exportJava(scenario);
                break;
            case "handoff-to-hpt" :
                handoff(scenario);
                break;
            case "verify-hpt-export" :
                verifyHptExport(scenario);
                break;
            case "import-posts" :
                importPosts(scenario);
                break;
            case "archive-posts" :
                archivePosts(scenario);
                break;
            case "hold-lock" :
                holdLock(scenario);
                break;
            case "verify-single-post" :
                assertEquals(1, scenario.echo.read().size());
                HptMessages.assertHptPost(scenario.echo.read().get(0).getMessage(), true);
                break;
            default :
                throw new IllegalArgumentException("Unknown worker command: " + command);
        }
        System.out.println("Verified " + command);
    }

    private static void holdLock(HptScenario scenario) throws Exception {
        scenario.echo.ensureExists();
        scenario.echo.locked(access -> {
            Files.writeString(scenario.root.resolve("lock-held"), "ready");
            awaitRelease(scenario.root);
            assertTrue(access.read().isEmpty());
            return null;
        });
    }

    private static void awaitRelease(Path root) throws IOException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (!Files.exists(root.resolve("release-lock"))) {
            if (System.nanoTime() > deadline) {
                throw new IOException("Timed out waiting for lock release");
            }
            pauseLockHolder();
        }
    }

    private static void pauseLockHolder() throws IOException {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted lock holder", e);
        }
    }

    private static void exportJava(HptScenario scenario) throws Exception {
        scenario.tosser.submitEchomail(HptMessages.fromJava());
        scenario.bridge.exportMessages();
        scenario.bridge.exportMessages();
        assertEquals(1, scenario.echo.read().size());
        assertEquals(0, scenario.echo.read().get(0).getAttributes() & JamArea.LOCAL);
    }

    private static void handoff(HptScenario scenario) throws Exception {
        assertTrue(packets(scenario).isEmpty());
        scenario.echo.locked(access -> {
            access.attributes(1, JamArea.LOCAL, JamArea.SENT);
            return null;
        });
    }

    private static void verifyHptExport(HptScenario scenario) throws Exception {
        List<Message> messages = new ArrayList<>();
        for (Path path : packets(scenario)) {
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(path)));
            assertEquals(HptScenario.LOCAL, packet.getOrigaddr());
            for (FtsPackMsg raw : packet.getMsgs()) {
                messages.add(new Message(raw, 2, 2));
            }
        }
        assertEquals(1, messages.size());
        Message message = messages.get(0);
        assertEquals(HptMessages.JAVA_ID, message.getMsgId());
        assertEquals("Java Тема", message.getSubject());
        assertEquals(HptMessages.TEXT, message.getBody().getBody().trim());
        assertEquals("2:500/101 aabbccdd", message.getBody().getKludges().get("REPLY").trim());
        assertFalse(message.getBody().toString().contains("SEEN-BY: SEEN-BY:"));
        assertFalse(message.getBody().toString().contains("PATH: \1PATH:"));
        assertTrue((scenario.echo.read().get(0).getAttributes() & JamArea.SENT) != 0);
    }

    private static List<Path> packets(HptScenario scenario) throws Exception {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(scenario.root)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".pkt")
                         || path.toString().endsWith(".out"))
                 .forEach(result::add);
        }
        return result;
    }

    private static void importPosts(HptScenario scenario) throws Exception {
        assertEquals(1, scenario.echo.read().size());
        assertEquals(1, scenario.netmail.read().size());
        HptMessages.assertHptPost(scenario.echo.read().get(0).getMessage(), true);
        Message netmail = scenario.netmail.read().get(0).getMessage();
        HptMessages.assertHptPost(netmail, false);
        assertEquals(new FTNAddr("2:500/101.3"), netmail.getTAddr());
        scenario.bridge.importOutgoing(scenario.tosser);
        scenario.bridge.importOutgoing(scenario.tosser);
        assertEquals(1, scenario.base.getMessageSize("test"));
        HptMessages.assertHptPost(scenario.base.getMessage("test",
                                                           scenario.echo.read()
                                                                        .get(0)
                                                                        .getMessage()
                                                                        .getMsgId()),
                                  true);
        assertEquals(1, scenario.base.getQueuedNetmail().size());
        HptMessages.assertHptPost(scenario.base.getQueuedNetmail().get(0).getMessage(), false);
        assertTrue((scenario.netmail.read().get(0).getAttributes() & JamArea.LOCKED) != 0);
    }

    private static void archivePosts(HptScenario scenario) throws Exception {
        Map<Path, byte[]> snapshot = snapshotJam(scenario.root.resolve("jam"));
        int count = JamArchiveImporter.importInto(scenario.echo, scenario.base).getImported();
        count += JamArchiveImporter.importInto(scenario.netmail, scenario.base).getImported();
        assertEquals(2, count);
        assertEquals(1,
                     JamArchiveImporter.importInto(scenario.echo, scenario.base).getDuplicates());
        assertEquals(1,
                     JamArchiveImporter.importInto(scenario.netmail, scenario.base)
                                       .getDuplicates());
        HptMessages.assertHptPost(scenario.base.getMessage("test",
                                                           scenario.echo.readOnly()
                                                                        .get(0)
                                                                        .getMessage()
                                                                        .getMsgId()),
                                  true);
        assertEquals(1, scenario.base.getEditorNetmail(HptScenario.LOCAL).size());
        HptMessages.assertHptPost(scenario.base.getEditorNetmail(HptScenario.LOCAL).get(0), false);
        scenario.tosser.exportEchomail();
        scenario.tosser.exportPendingNetmail();
        assertTrue(scenario.base.getQueuedNetmail().isEmpty());
        assertTrue(new OutboundSpool(scenario.config).pending(scenario.config.getLink(HptScenario.PEER),
                                                              true)
                                                     .isEmpty());
        Map<Path, byte[]> after = snapshotJam(scenario.root.resolve("jam"));
        assertEquals(snapshot.keySet(), after.keySet());
        for (Map.Entry<Path, byte[]> entry : snapshot.entrySet()) {
            assertArrayEquals(entry.getValue(),
                              after.get(entry.getKey()),
                              entry.getKey().toString());
        }
    }

    private static Map<Path, byte[]> snapshotJam(Path directory) throws IOException {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toArray(Path[]::new)) {
                result.put(path, Files.readAllBytes(path));
            }
        }
        return result;
    }
}
