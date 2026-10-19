package org.fidonet.mail.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class LegacyEditorSubmissionTest {
    @TempDir
    Path temporary;

    private JFtnConfig config(String format, String options) throws Exception {
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
                      + "\nLegacyNetmailFormat="
                      + format
                      + "\n"
                      + options;
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private Message outgoing(boolean echo, String destination) {
        return new Message("Sysop",
                           "Remote",
                           new FTNAddr("1:500/100"),
                           new FTNAddr(destination),
                           echo ? "Echo" : "Netmail",
                           (echo ? "AREA:TEST\r" : "") + "\1CHRS: UTF-8 4\rEditor message ї є\r",
                           new Date(1700000000000L));
    }

    private MsgNetmailMailbox msgMailbox() {
        return new MsgNetmailMailbox(temporary.resolve("jam/netmail-msg"),
                                     StandardCharsets.UTF_8,
                                     new FTNAddr("1:500/100"));
    }

    private void editorNetmail(LegacyMailStore store, String format, String destination)
            throws Exception {
        Message message = outgoing(false, destination);
        if (format.equals("msg")) {
            msgMailbox().append(message, MsgNetmailMailbox.LOCAL | 1);
        } else {
            store.mailbox(null).append(message, JamArea.LOCAL | JamArea.NET);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jam", "msg"})
    void scannedEditorNetmailAndEchoIntoRealPacketsWithoutRepeatingOnRestart(String format)
            throws Exception {
        JFtnConfig config = config(format, "");
        LegacyMailStore store = new LegacyMailStore(config);
        Link link = config.getLink(new FTNAddr("1:500/101"));
        store.configureSubscription(link, "test", AccessLevel.BOTH);
        store.mailbox("test").ensureExists();
        store.mailbox("test").append(outgoing(true, "1:500/101"), JamArea.LOCAL | JamArea.ECHO);
        editorNetmail(store, format, "1:500/101");
        process(store, config);
        OutboundSpool spool = new OutboundSpool(config);
        List<Path> packets = spool.pending(link, true);
        assertEquals(2, packets.size());
        boolean echo = false;
        boolean netmail = false;
        for (Path path : packets) {
            assertTrue(path.getFileName().toString().matches("[0-9a-f]{8}\\.pkt"));
            FtsPkt packet = new FtsPkt(ByteBuffer.wrap(Files.readAllBytes(path)));
            assertEquals(1, packet.getMsgs().length);
            Message message = new Message(packet.getMsgs()[0],
                                          packet.getOrigZone(),
                                          packet.getDestZone());
            assertEquals(new FTNAddr("1:500/100"), message.getFAddr());
            assertEquals(new FTNAddr("1:500/101"), message.getTAddr());
            assertTrue(message.getBody().getBody().contains("Editor message ї є"));
            assertTrue(message.getMsgId().matches("1:500/100 [0-9a-f]{8}"));
            echo |= message.isEchomail();
            netmail |= !message.isEchomail();
            if (!message.isEchomail()) {
                assertFalse(message.getBody().getViaLines().isEmpty());
            }
        }
        assertTrue(echo && netmail);
        store.close();
        try (LegacyMailStore reopened = new LegacyMailStore(config)) {
            clearSent(reopened, format);
            process(reopened, config);
            assertEquals(packets, spool.pending(link, true));
        }
    }

    private void clearSent(LegacyMailStore store, String format) throws Exception {
        if (format.equals("msg")) {
            Path path = temporary.resolve("jam/netmail-msg/1.msg");
            byte[] bytes = Files.readAllBytes(path);
            bytes[186] &= ~8;
            Files.write(path, bytes);
        } else {
            store.mailbox(null).locked(access -> {
                access.attributes(1, 0, JamArea.SENT);
                return null;
            });
        }
    }

    private void process(LegacyMailStore store, JFtnConfig config) throws Exception {
        Tosser tosser = new Tosser(config, store);
        store.beforeExport(tosser);
        tosser.exportPendingNetmail();
        tosser.exportEchomail();
        store.afterExport();
    }

    @Test
    void retainedIncomingMsgAndEditorDeletionAndGeneratedCorrectCatalogs() throws Exception {
        JFtnConfig config = config("msg", "");
        Message incoming = new Message("Remote",
                                       "Sysop",
                                       new FTNAddr("1:500/101"),
                                       new FTNAddr("1:500/100"),
                                       "Incoming",
                                       "\1MSGID: 1:500/101 abcdef01\rBody\r",
                                       new Date(1700000000000L));
        incoming.setUpLink(new FTNAddr("1:500/101"));
        try (LegacyMailStore store = new LegacyMailStore(config)) {
            store.saveNetmail(incoming, "local", null);
            assertEquals("Incoming",
                         store.getLocalNetmail(new FTNAddr("1:500/100")).get(0).getSubject());
        }
        Path message = temporary.resolve("jam/netmail-msg/1.msg");
        assertTrue(Files.exists(message));
        assertFalse(Files.exists(temporary.resolve("jam/netmail.jhr")));
        String areas = Files.readString(temporary.resolve("editor/areas.lst"));
        assertTrue(areas.contains("FIDOMSGTYPE FTS1"));
        assertTrue(areas.contains("Net FTS1"));
        assertTrue(Files.readString(temporary.resolve("editor/fidoconfig.areas"))
                        .contains(" -b Msg -a 1:500/100"));
        Files.delete(message);
        try (LegacyMailStore reopened = new LegacyMailStore(config)) {
            process(reopened, config);
            assertTrue(reopened.getLocalNetmail(new FTNAddr("1:500/100")).isEmpty());
        }
        assertFalse(Files.exists(message));
        assertThrows(IOException.class, () -> new LegacyMailStore(config("jam", "")));
        Files.delete(temporary.resolve("jam/netmail-msg"));
        assertThrows(IOException.class, () -> new LegacyMailStore(config));
    }

    @Test
    void leftMsgUnsentOnMissingRouteAndReusedItsIdAfterRestart() throws Exception {
        JFtnConfig initial = config("msg", "");
        String id;
        try (LegacyMailStore store = new LegacyMailStore(initial)) {
            editorNetmail(store, "msg", "1:500/999");
            assertThrows(IllegalStateException.class,
                         () -> store.beforeExport(new Tosser(initial, store)));
            Message pending = msgMailbox().read().get(0);
            id = pending.getMsgId();
            assertTrue(id.matches("1:500/100 [0-9a-f]{8}"));
            assertEquals(0, pending.getAttributes() & MsgNetmailMailbox.SENT);
        }
        JFtnConfig routed = config("msg", "DefaultRoute=1:500/101\n");
        try (LegacyMailStore store = new LegacyMailStore(routed)) {
            process(store, routed);
            assertEquals(id, msgMailbox().read().get(0).getMsgId());
            assertTrue((msgMailbox().read().get(0).getAttributes() & MsgNetmailMailbox.SENT) != 0);
            assertEquals(1,
                         new OutboundSpool(routed).pending(routed.getLink(new FTNAddr("1:500/101")),
                                                           true)
                                                  .size());
        }
    }
}
