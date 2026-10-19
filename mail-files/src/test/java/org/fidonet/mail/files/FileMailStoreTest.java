package org.fidonet.mail.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

class FileMailStoreTest {
    @TempDir
    Path temporary;

    JFtnConfig config(String profile) throws Exception {
        JFtnConfig result = new JFtnConfig();
        String text = "SysOp=Test\nRuntimeProfile="
                      + profile
                      + "\nLink=1:500/101,1:500/100,-\nLink=1:500/102,1:500/100,-\n"
                      + "Inbound="
                      + temporary.resolve("in")
                      + "\nOutbound="
                      + temporary.resolve("out")
                      + "\nTmp="
                      + temporary.resolve("tmp")
                      + "\nEchoPath="
                      + temporary.resolve("jam")
                      + "\n";
        result.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    Message message(boolean echo) {
        Message result = new Message("Sender",
                                     "Recipient",
                                     new FTNAddr("1:500/101"),
                                     new FTNAddr("1:500/102"),
                                     "Unicode ї є",
                                     (echo ? "AREA:TEST\r" : "")
                                     + "\1MSGID: 1:500/101 12345678\r\1CHRS: UTF-8 4\rBody ї є\r",
                                     new Date(1700000000000L));
        result.setUpLink(new FTNAddr("1:500/101"));
        return result;
    }

    @Test
    void frozeNetmailRouteAndPreservedIdentityAndSourceAcrossRestart() throws Exception {
        JFtnConfig config = config("transit");
        FileMailStore store = new FileMailStore(config);
        store.saveNetmail(message(false), "original", "1:500/102.0");
        store.close();
        FileMailStore reopened = new FileMailStore(config);
        reopened.saveNetmail(message(false), "original", "1:500/999.0");
        assertEquals("1:500/102.0", reopened.getQueuedNetmail().get(0).getNextHop());
        Message restored = reopened.getQueuedNetmail().get(0).getMessage();
        assertEquals(MailIdentity.of(message(false)), MailIdentity.of(restored));
        assertEquals(message(false).getUpLink(), restored.getUpLink());
        assertEquals(message(false).getBody().toString(), restored.getBody().toString());
        assertFalse(reopened.isDuplicateNetmail(restored, "original"));
        assertTrue(reopened.isDuplicateNetmail(restored, "another-delivery"));
        reopened.markNetmailSent("original");
        reopened.close();
        FileMailStore completed = new FileMailStore(config);
        assertTrue(completed.getQueuedNetmail().isEmpty());
        assertTrue(completed.isDuplicateNetmail(restored, "new-delivery"));
        completed.close();
    }

    @Test
    void frozeEchoFanoutBeforeAcceptanceAndDidNotBackfillNewSubscribers() throws Exception {
        JFtnConfig config = config("transit");
        FileMailStore store = new FileMailStore(config);
        Link first = config.getLink(new FTNAddr("1:500/102"));
        Link second = config.getLink(new FTNAddr("1:500/101"));
        store.configureSubscription(first, "test", AccessLevel.BOTH);
        store.addMessage(message(true), "test");
        store.removeEchoSubscription(first, "test");
        store.configureSubscription(second, "test", AccessLevel.BOTH);
        store.afterExport();
        store.close();
        FileMailStore reopened = new FileMailStore(config);
        OutboundSpool spool = new OutboundSpool(config);
        assertEquals(1, spool.pending(first, true).size());
        assertTrue(spool.pending(second, true).isEmpty());
        reopened.addMessage(message(true), "test");
        assertEquals(1, spool.pending(first, true).size());
        assertTrue(reopened.isDupe(message(true)));
        assertEquals(0, reopened.getMessageSize("test"));
        reopened.close();
    }

    @Test
    void retainedLocalTransitMailAndRejectedImplicitProfileSwitch() throws Exception {
        JFtnConfig config = config("transit");
        FileMailStore store = new FileMailStore(config);
        store.saveNetmail(message(false), "local", null);
        store.close();
        FileMailStore reopened = new FileMailStore(config);
        assertEquals(1, reopened.getLocalNetmail(new FTNAddr("1:500/102")).size());
        assertTrue(reopened.getQueuedNetmail().isEmpty());
        reopened.close();
        assertThrows(IllegalStateException.class, () -> new FileMailStore(config("legacy")));
    }

    @Test
    void rejectedConcurrentWritersAndReleasedLockAfterFailedOpen() throws Exception {
        FileMailStore store = new FileMailStore(config("transit"));
        assertThrows(UncheckedIOException.class, () -> new FileMailStore(config("transit")));
        store.close();
        assertFalse(store.open());
        assertThrows(IllegalStateException.class,
                     () -> store.saveNetmail(message(false), "late", null));
        assertThrows(IllegalStateException.class, () -> new FileMailStore(config("legacy")));
        FileMailStore reopened = new FileMailStore(config("transit"));
        reopened.close();
    }
    @Test
    void refusedToReinitializeAMissingCatalogOverExistingMail() throws Exception {
        JFtnConfig config = config("transit");
        FileMailStore store = new FileMailStore(config);
        store.saveNetmail(message(false), "local", null);
        store.close();
        Path catalog = temporary.resolve("tmp/mail-state/catalog.properties");
        byte[] backup = Files.readAllBytes(catalog);
        Files.delete(catalog);
        assertThrows(IllegalStateException.class, () -> new FileMailStore(config));
        Files.write(catalog, backup);
        try (FileMailStore restored = new FileMailStore(config)) {
            assertEquals(1, restored.getLocalNetmail(new FTNAddr("1:500/102")).size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"body", "identity"})
    void refusedChangedSnapshotsOrIdentityReceiptsInsteadOfForwardingOrDroppingMail(String field)
            throws Exception {
        try (FileMailStore store = new FileMailStore(config("transit"))) {
            store.saveNetmail(message(false), "delivery", "1:500/102.0");
            Path path = FileRecords.list(temporary.resolve("tmp/mail-state/netmail")).get(0);
            Properties original = FileRecords.read(path);
            Properties changed = new Properties();
            changed.putAll(original);
            changed.setProperty(field, "changed");
            FileRecords.write(path, changed);
            if (field.equals("body")) {
                List<Message> delivered = new ArrayList<>();
                assertThrows(IllegalStateException.class,
                             () -> store.visitQueuedNetmail(queued -> delivered.add(queued.getMessage())));
                assertTrue(delivered.isEmpty());
            } else {
                assertThrows(IllegalStateException.class,
                             () -> store.isDuplicateNetmail(message(false), "replay"));
            }
            FileRecords.write(path, original);
            assertEquals(1, store.getQueuedNetmail().size());
        }
    }

}
