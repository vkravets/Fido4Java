package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TosserBundleTest {
    @TempDir
    public File temporary;
    private OrmManager orm;
    private DatabaseManager base;

    @BeforeEach
    public void open() throws Exception {
        orm = new OrmManager("jdbc:h2:mem:toss" + System.nanoTime());
        orm.connect();
        base = new DatabaseManager(orm);
        assertTrue(base.createArea("test"));
    }

    @AfterEach
    public void close() {
        base.close();
    }

    private Tosser tosser(Path bad, int delete, boolean known) {
        Link link = new Link("2:500/101, 2:500/102, secret, localhost");
        JFtnConfig config = new JFtnConfig() {
            @Override
            public String getValue(String key, String defaultValue) {
                if (key.equals("EnforceEchoAccess")) {
                    return "false";
                }
                return super.getValue(key, defaultValue);
            }

            @Override
            public Link getLink(FTNAddr address) {
                return known ? link : null;
            }

            @Override
            public String getTmpDir() {
                return bad.toString();
            }

            @Override
            public Integer isDeleteTossedFiles() {
                return delete;
            }
        };
        try {
            return new Tosser(config, base);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private byte[] packet(String password, String... texts) throws IOException {
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        for (String text : texts) {
            ByteBuffer header = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
            for (int number : new int[]{2, 101, 102, 500, 500, 0, 0}) {
                header.putShort((short) number);
            }
            header.put("12 Oct 26  12:34:56".getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
            messages.write(header.array());
            for (String field : new String[]{"Reader", "Author", "Subject", text}) {
                messages.write(field.getBytes(StandardCharsets.US_ASCII));
                messages.write(0);
            }
        }
        ByteBuffer packet = ByteBuffer.allocate(60 + messages.size())
                                      .order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort(0, (short) 101);
        packet.putShort(2, (short) 102);
        packet.putShort(18, (short) 2);
        packet.putShort(20, (short) 500);
        packet.putShort(22, (short) 500);
        packet.putShort(34, (short) 2);
        packet.putShort(36, (short) 2);
        packet.position(26);
        packet.put(password.getBytes(StandardCharsets.US_ASCII));
        packet.position(58);
        packet.put(messages.toByteArray()).putShort((short) 0);
        return packet.array();
    }

    private Path bundle(Path inbound, String name, byte[] bytes) throws IOException {
        Path path = inbound.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("empty/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("message.pkt"));
            zip.write(bytes);
            zip.closeEntry();
        }
        return path;
    }

    @Test
    public void tossesEchomailSkipsDuplicatesAndLeavesNetmailOutOfEchoDatabase() throws Exception {
        Path inbound = Files.createDirectory(temporary.toPath().resolve("inbound"));
        Path bad = Files.createDirectory(temporary.toPath().resolve("bad"));
        String echo = "AREA:TEST\r\1MSGID: 2:500/101 unique\rHello\r";
        Path bundle = bundle(inbound, "mail.mo0", packet("secret", echo, echo, "Netmail\r"));
        Tosser tosser = tosser(bad, 0, true);
        tosser.runFast(inbound.toString());
        assertEquals(1, base.getMessageSize("test"));
        Message message = base.getMessages("test").next();
        assertEquals("TEST", message.getArea());
        assertEquals("Hello\r", message.getBody().getBody());
        assertTrue(Files.exists(bundle));
        assertEquals(Collections.singletonList("test"), tosser.getAreamgr().getEchos());
        assertTrue(tosser.getAreamgr().isEchoExists("test"));
    }

    @Test
    public void savesRejectedPacketsAndDeletesProcessedBundleOnlyWhenConfigured() throws Exception {
        for (boolean known : new boolean[]{false, true}) {
            Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
            Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
            byte[] bytes = packet("wrong");
            Path bundle = bundle(inbound, "mail.fr0", bytes);
            tosser(bad, 1, known).runFast(inbound.toString());
            assertArrayEquals(bytes, Files.readAllBytes(bad.resolve("message.bad")));
            assertFalse(Files.exists(bundle));
            assertEquals(0, base.getMessageSize("test"));
        }
    }

    @Test
    public void corruptArchiveOrPacketIsRetainedForDiagnosis() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        Path archive = inbound.resolve("broken.we0");
        Files.writeString(archive, "not a zip");
        Path packet = bundle(inbound, "packet.sa0", new byte[]{1, 2});
        tosser(bad, 1, true).runFast(inbound.toString());
        assertTrue(Files.exists(archive));
        assertTrue(Files.exists(packet));
        assertEquals(0, base.getMessageSize("test"));
    }

    @Test
    public void quarantinesUnhandledNetmailBeforeDeletingItsBundle() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        byte[] bytes = packet("secret", "\1INTL 2:500/999 2:500/101\rNetmail\r");
        Path source = bundle(inbound, "mail.mo0", bytes);
        tosser(bad, 1, true).runFast(inbound.toString());
        assertArrayEquals(bytes, Files.readAllBytes(bad.resolve("message.bad")));
        assertFalse(Files.exists(source));
    }

    @Test
    public void retainsBundleWhenQuarantineNameContainsAnotherPacket() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        Files.writeString(bad.resolve("message.bad"), "previous packet");
        Path source = bundle(inbound, "mail.mo0", packet("wrong"));
        tosser(bad, 1, true).runFast(inbound.toString());
        assertTrue(Files.exists(source));
        assertEquals("previous packet", Files.readString(bad.resolve("message.bad")));
    }

    @Test
    public void resumesPartialImportAfterStorageFailureAndTosserRestart() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        DatabaseManager normal = base;
        base = new DatabaseManager(orm) {
            @Override
            public void addMessage(Message message, String area) {
                if (message.getMsgId().endsWith("second")) {
                    throw new IllegalStateException("Simulated storage outage");
                }
                super.addMessage(message, area);
            }
        };
        Path source = bundle(inbound,
                             "mail.mo0",
                             packet("secret",
                                    "AREA:TEST\r\1MSGID: 2:500/101 first\rFirst\r",
                                    "AREA:TEST\r\1MSGID: 2:500/101 second\rSecond\r"));
        tosser(bad, 1, true).runFast(inbound.toString());
        assertTrue(Files.exists(source));
        assertEquals(1, base.getMessageSize("test"));
        base = normal;
        tosser(bad, 1, true).runFast(inbound.toString());
        assertEquals(2, base.getMessageSize("test"));
        assertFalse(Files.exists(source));
    }

    @Test
    public void importsLoosePacketAndQuarantinesPacketsForAnotherStation() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        byte[] bytes = packet("secret", "AREA:TEST\r\1MSGID: 2:500/101 loose\rHello\r");
        Path accepted = inbound.resolve("accepted.PKT");
        Files.write(accepted, bytes);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(2, (short) 999);
        Files.write(inbound.resolve("wrong.pkt"), bytes);
        tosser(bad, 1, true).runFast(inbound.toString());
        assertEquals(1, base.getMessageSize("test"));
        assertFalse(Files.exists(accepted));
        assertArrayEquals(bytes, Files.readAllBytes(bad.resolve("wrong.bad")));
    }

    @Test
    public void preservesBundleContainingUnexpectedFiles() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        Path source = inbound.resolve("mail.mo0");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("notes.txt"));
            zip.write("unexpected attachment".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        tosser(bad, 1, true).runFast(inbound.toString());
        assertTrue(Files.exists(source));
    }

    @Test
    public void deliversMultipleLocalNetmailsAndKeepsThemOutOfEchoAreas() throws Exception {
        Path inbound = Files.createTempDirectory(temporary.toPath(), "test-");
        Path bad = Files.createTempDirectory(temporary.toPath(), "test-");
        Path source = bundle(inbound,
                             "mail.mo0",
                             packet("secret", "First netmail\r", "Second netmail\r"));
        Tosser tosser = tosser(bad, 0, true);
        tosser.runFast(inbound.toString());
        tosser.runFast(inbound.toString());
        assertTrue(Files.exists(source));
        assertEquals(2, base.getLocalNetmail(new FTNAddr("2:500/102")).size());
        assertEquals("First netmail\r",
                     base.getLocalNetmail(new FTNAddr("2:500/102")).get(0).getBody().getBody());
        assertEquals(0, base.getMessageSize("test"));
    }
}
