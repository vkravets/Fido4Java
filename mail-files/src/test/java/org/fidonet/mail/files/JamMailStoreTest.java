package org.fidonet.mail.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class JamMailStoreTest {
    @TempDir
    Path temporary;

    private JFtnConfig config() throws Exception {
        return config("");
    }

    private JFtnConfig config(String options) throws Exception {
        JFtnConfig result = new JFtnConfig();
        String text = "SysOp=Test\nRuntimeProfile=legacy\nLink=1:500/101,1:500/100,-\n"
                      + "Inbound="
                      + temporary.resolve("in")
                      + "\nOutbound="
                      + temporary.resolve("out")
                      + "\nTmp="
                      + temporary.resolve("tmp")
                      + "\nEchoPath="
                      + temporary.resolve("jam")
                      + "\nEditorConfigPath="
                      + temporary.resolve("editor")
                      + "\n";
        result.load(new ByteArrayInputStream((text + options).getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private Message outgoing(boolean echo, boolean id) {
        return new Message("Local",
                           "Remote",
                           new FTNAddr("1:500/100"),
                           new FTNAddr("1:500/101"),
                           "Test",
                           (echo ? "AREA:TEST\r" : "")
                           + (id ? "\1MSGID: 1:500/100 12345678\r" : "")
                           + "\1CHRS: UTF-8 4\rUnicode ї є\r",
                           new Date(1700000000000L));
    }

    @Test
    void queuedEditorPostsOnceAndUsedSntOnRescanAndRestart() throws Exception {
        JFtnConfig config = config();
        JamMailStore store = new JamMailStore(config);
        store.configureSubscription(config.getLink(new FTNAddr("1:500/101")),
                                    "test",
                                    AccessLevel.BOTH);
        store.mailbox("test").ensureExists();
        store.mailbox("test").append(outgoing(true, false), JamArea.LOCAL | JamArea.ECHO);
        store.mailbox(null).append(outgoing(false, true), JamArea.LOCAL | JamArea.NET);
        store.beforeExport(new Tosser(config, store));
        assertEquals(1, store.getQueuedNetmail().size());
        assertEquals(1, store.getMessageSize("test"));
        assertTrue((store.mailbox("test").read().get(0).getAttributes() & JamArea.SENT) != 0);
        assertTrue(store.mailbox("test")
                        .read()
                        .get(0)
                        .getMessage()
                        .getMsgId()
                        .matches("1:500/100 [0-9a-f]{8}"));
        store.close();
        JamMailStore reopened = new JamMailStore(config);
        reopened.beforeExport(new Tosser(config, reopened));
        assertEquals(1, reopened.getQueuedNetmail().size());
        assertEquals(1, reopened.getMessageSize("test"));
        // Simulate a crash after queue acceptance but before Snt became durable.
        reopened.mailbox(null).locked(access -> {
            access.attributes(1, 0, JamArea.SENT);
            return null;
        });
        reopened.beforeExport(new Tosser(config, reopened));
        assertEquals(1, reopened.getQueuedNetmail().size());
        assertTrue((reopened.mailbox(null).read().get(0).getAttributes() & JamArea.SENT) != 0);
        reopened.close();
    }

    @Test
    void preservedEditorDeletionAndGeneratedBothCatalogFormatsWithoutOverwritingCustomization()
            throws Exception {
        JFtnConfig config = config();
        JamMailStore store = new JamMailStore(config);
        store.createArea("test");
        Message message = outgoing(true, true);
        message.setUpLink(new FTNAddr("1:500/101"));
        store.addMessage(message, "test");
        assertFalse((store.mailbox("test").read().get(0).getAttributes() & JamArea.LOCAL) != 0);
        store.mailbox("test").locked(access -> {
            access.attributes(1, JamArea.DELETED, 0);
            return null;
        });
        Path custom = temporary.resolve("editor/golded.cfg.example");
        Files.writeString(custom, "; operator customization");
        store.beforeExport(new Tosser(config, store));
        store.close();
        JamMailStore reopened = new JamMailStore(config);
        assertEquals(0, reopened.getMessageSize("test"));
        assertTrue(reopened.isDupe(message));
        assertEquals("; operator customization", Files.readString(custom));
        assertTrue(Files.readString(temporary.resolve("editor/areas.lst"))
                        .contains("AREADEF test"));
        assertTrue(Files.readString(temporary.resolve("editor/fidoconfig.areas"))
                        .contains(" -b Jam -a 1:500/100"));
        reopened.close();
        assertThrows(IOException.class,
                     () -> new JamMailStore(config("JamEcho=test,"
                                                   + temporary.resolve("moved/test")
                                                   + ",1:500/100\n")));
    }
    @Test
    void honoredCustomBindingsAndRejectedMissingPrimaryFiles() throws Exception {
        Path custom = temporary.resolve("custom-jam/test");
        JFtnConfig config = config("JamEcho=test," + custom + ",1:500/100\n");
        JamMailStore store = new JamMailStore(config);
        store.addMessage(outgoing(true, true), "test");
        assertEquals(custom.toAbsolutePath(), store.mailbox("test").getPath());
        store.close();
        Path text = Path.of(custom + ".jdt");
        byte[] backup = Files.readAllBytes(text);
        Files.delete(text);
        assertThrows(IOException.class, () -> new JamMailStore(config));
        Files.write(text, backup);
        JamMailStore reopened = new JamMailStore(config);
        assertEquals(1, reopened.getMessageSize("test"));
        reopened.close();
        assertThrows(IOException.class,
                     () -> new JamMailStore(config("JamRoot="
                                                   + temporary.resolve("different")
                                                   + "\n")));
    }

    @Test
    void leftSntClearUntilRoutingSucceededAndRecoveredPreparedSubmission() throws Exception {
        JFtnConfig config = config();
        JamMailStore store = new JamMailStore(config);
        Message message = new Message("Local",
                                      "Remote",
                                      new FTNAddr("1:500/100"),
                                      new FTNAddr("1:500/999"),
                                      "Deferred",
                                      "\1CHRS: UTF-8 4\rNeeds route\r",
                                      new Date(1700000000000L));
        store.mailbox(null).append(message, JamArea.LOCAL | JamArea.NET);
        assertThrows(IllegalStateException.class,
                     () -> store.beforeExport(new Tosser(config, store)));
        assertTrue(store.getQueuedNetmail().isEmpty());
        assertFalse((store.mailbox(null).read().get(0).getAttributes() & JamArea.SENT) != 0);
        String id = store.mailbox(null).read().get(0).getMessage().getMsgId();
        store.close();
        JFtnConfig routed = config("DefaultRoute=1:500/101\n");
        JamMailStore reopened = new JamMailStore(routed);
        reopened.beforeExport(new Tosser(routed, reopened));
        assertEquals(id, reopened.getQueuedNetmail().get(0).getMessage().getMsgId());
        assertTrue((reopened.mailbox(null).read().get(0).getAttributes() & JamArea.SENT) != 0);
        reopened.close();
    }

    @Test
    void preservedDeletionOfReceivedMailWithoutMsgidAndWithNoncanonicalDate() throws Exception {
        JFtnConfig config = config();
        Message original = new Message("Remote",
                                       "Local",
                                       new FTNAddr("1:500/101"),
                                       new FTNAddr("1:500/100"),
                                       "No MSGID",
                                       "\1CHRS: UTF-8 4\rReceived without identifier\r",
                                       new Date(1700000000000L));
        byte[] bytes = PacketWriter.write(original.getFAddr(),
                                          original.getTAddr(),
                                          "",
                                          List.of(original));
        bytes[58 + 14] = ' ';
        FtsPkt packet = new FtsPkt(ByteBuffer.wrap(bytes));
        Message received = new Message(packet.getMsgs()[0], 1, 1);
        assertTrue(received.getMsgDate().startsWith(original.getMsgDate().substring(1, 2) + " "));
        assertTrue(received.getMsgId() == null);
        received.setUpLink(original.getFAddr());
        JamMailStore store = new JamMailStore(config);
        store.saveNetmail(received, "raw-date", null);
        store.mailbox(null).locked(access -> {
            access.attributes(1, JamArea.DELETED, 0);
            return null;
        });
        store.close();
        JamMailStore reopened = new JamMailStore(config);
        assertTrue(reopened.mailbox(null).read().isEmpty());
        assertTrue(reopened.getLocalNetmail(new FTNAddr("1:500/100")).isEmpty());
        reopened.close();
    }

    @Test
    void rejectedInvalidBindingsWithoutPublishingAnEmptyArea() throws Exception {
        JFtnConfig config = config();
        JamMailStore initial = new JamMailStore(config);
        initial.close();
        String invalid = "JamEcho=bad," + temporary.resolve("jam/netmail") + ",1:500/100\n";
        assertThrows(IOException.class, () -> new JamMailStore(config(invalid)));
        JamMailStore reopened = new JamMailStore(config);
        assertTrue(reopened.getAreas().isEmpty());
        reopened.close();
    }

}
