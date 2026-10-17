package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.MailLinkConfiguration;
import org.fidonet.jftn.rpc.TosserConfiguration;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class MailConfigurationStoreTest {
    @Test
    void requiredExplicitCreationModesAndSavedOnlyTheSubmittedRules() throws Exception {
        station();
        Files.writeString(path(), Files.readString(path()) + "EchoSubscriptions=database\n");
        MailConfigurationStore store = new MailConfigurationStore(new NodeConfigurationStore(path()));
        List<MailLinkConfiguration> links = store.links();
        assertEquals("NONE", links.get(0).getAutoCreateEchoAccess());
        links.get(0).setAutoCreateEchoAccess("WRITE");
        links.get(1).setAutoCreateEchoAccess("BOTH");
        store.saveLinks(links);
        assertEquals("WRITE", store.links().get(0).getAutoCreateEchoAccess());
        links.get(0).unsetAutoCreateEchoAccess();
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        links.get(0).setAutoCreateEchoAccess("WRITE");
        links.get(1).setAutoCreateEchoAccess("NONE");
        store.saveLinks(links);
        assertEquals("WRITE", store.links().get(0).getAutoCreateEchoAccess());
        assertEquals("NONE", store.links().get(1).getAutoCreateEchoAccess());
        byte[] accepted = Files.readAllBytes(path());
        links.get(0).setAutoCreateEchoAccess("READ");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        assertArrayEquals(accepted, Files.readAllBytes(path()));
    }

    @TempDir
    Path directory;

    private Path path() {
        return directory.resolve("jftn.conf");
    }

    private NodeConfigurationStore station() throws Exception {
        Files.writeString(path(),
                          "Inbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\nSysOp=Test\n"
                          + "# Preserve this comment\nCustomOption=retained\n"
                          + "Link=1:500/101,1:500/100,session,example.org:24554\n"
                          + "Link=1:500/102,1:500/100.1,other,other.example:24555\n");
        return new NodeConfigurationStore(path());
    }

    private JFtnConfig loaded() throws Exception {
        JFtnConfig result = new JFtnConfig();
        result.load(path().toString());
        return result;
    }

    @Test
    void editedPacketAndSessionPasswordsIndependentlyAndNeverReturnedSecrets() throws Exception {
        NodeConfigurationStore station = station();
        MailConfigurationStore store = new MailConfigurationStore(station);
        List<MailLinkConfiguration> links = store.links();
        assertEquals("INHERIT", links.get(0).getPacketPasswordMode());
        links.get(0)
             .setSessionPassword("long-session-password")
             .setPacketPasswordMode("SEPARATE")
             .setPacketPassword("packet");
        store.saveLinks(links);
        JFtnConfig config = loaded();
        assertEquals("packet",
                     config.getPacketPassword(config.getConfiguredLinks()
                                                    .stream()
                                                    .filter(link -> link.getAddr().getNode() == 101)
                                                    .findFirst()
                                                    .orElseThrow()));
        assertEquals("long-session-password", new Link(config.getValue("Link")).getPass());
        List<MailLinkConfiguration> read = store.links();
        assertEquals("", read.get(0).getSessionPassword());
        assertEquals("", read.get(0).getPacketPassword());
        assertEquals("SEPARATE", read.get(0).getPacketPasswordMode());
        read.get(0).setHost("changed.example");
        store.saveLinks(read);
        assertTrue(Files.readString(path()).contains("packetpassword=1:500/101.0,packet"));
        assertTrue(Files.readString(path()).contains("other,other.example:24555"));
        assertTrue(Files.readString(path()).contains("# Preserve this comment"));
        assertTrue(Files.readString(path()).contains("CustomOption=retained"));
    }

    @Test
    void switchedBetweenSeparateUnprotectedAndInheritedPasswords() throws Exception {
        MailConfigurationStore store = new MailConfigurationStore(station());
        List<MailLinkConfiguration> links = store.links();
        links.get(0).setPacketPasswordMode("UNPROTECTED");
        store.saveLinks(links);
        assertEquals("UNPROTECTED", store.links().get(0).getPacketPasswordMode());
        assertTrue(Files.readString(path()).contains("packetpassword=1:500/101.0,-"));
        links.get(0).setPacketPasswordMode("INHERIT");
        store.saveLinks(links);
        assertFalse(Files.readString(path()).contains("packetpassword="));
        links.get(0).setSessionPassword("-");
        store.saveLinks(links);
        assertEquals("", new Link(loaded().getValue("Link")).getPass());
    }

    @Test
    void addedLinksAndRefusedRemovalDuplicatesInjectionAndInvalidPacketPasswords()
            throws Exception {
        MailConfigurationStore store = new MailConfigurationStore(station());
        List<MailLinkConfiguration> links = store.links();
        links.add(new MailLinkConfiguration("1:500/103",
                                            List.of("1:500/100"),
                                            "new.example",
                                            24554,
                                            "",
                                            "",
                                            "INHERIT",
                                            "NONE"));
        store.saveLinks(links);
        assertEquals(3, loaded().getConfiguredLinks().size());
        String before = Files.readString(path());
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links.subList(0, 1)));
        links.get(2).setAddress("1:500/101.0");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        links.get(2).setAddress("1:500/103").setSessionPassword("bad\nSetting=true");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        links.get(2).setSessionPassword("too-long-for-pkt");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        links.get(2).setPacketPasswordMode("SEPARATE").setPacketPassword("123456789");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        links.get(2).setPacketPassword("\u043f\u0430\u0440\u043e\u043b\u044c");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        links.get(2).setPacketPassword("");
        assertThrows(IllegalArgumentException.class, () -> store.saveLinks(links));
        assertEquals(before, Files.readString(path()));
    }

    @Test
    void retainedIncomingOnlyLinksWithoutInventingAnOutgoingEndpoint() throws Exception {
        MailConfigurationStore store = new MailConfigurationStore(station());
        var links = store.links();
        links.get(1).setHost("");
        store.saveLinks(links);
        assertEquals("", store.links().get(1).getHost());
        assertTrue(Files.readString(path()).contains("link=1:500/102.0,1:500/100.1,other\n"));
    }

    @Test
    void sharedWriterKeptTosserAndLinkEditsAndRefusedExternalChanges() throws Exception {
        NodeConfigurationStore station = station();
        MailConfigurationStore store = new MailConfigurationStore(station);
        TosserConfiguration defaults = store.tosser();
        assertEquals(5, defaults.getIntervalSeconds());
        assertTrue(defaults.isDeleteProcessedFiles());
        assertTrue(defaults.isEnforceEchoAccess());
        assertFalse(defaults.isAutoCreateEcho());
        store.saveTosser(new TosserConfiguration(30, false, true, true));
        List<MailLinkConfiguration> links = store.links();
        links.get(0).setPacketPasswordMode("SEPARATE").setPacketPassword("packet");
        store.saveLinks(links);
        assertEquals(30, store.tosser().getIntervalSeconds());
        assertFalse(store.tosser().isDeleteProcessedFiles());
        assertTrue(store.tosser().isAutoCreateEcho());
        assertThrows(IllegalArgumentException.class,
                     () -> store.saveTosser(new TosserConfiguration(0, true, true, false)));
        Files.writeString(path(), Files.readString(path()) + "# external\n");
        assertThrows(Exception.class, () -> store.saveTosser(defaults));
        assertTrue(Files.readString(path()).endsWith("# external\n"));
    }

    @Test
    void refusedToSilentlyMaskOptionsDefinedInConfigurationFragments() throws Exception {
        NodeConfigurationStore station = station();
        Files.createDirectory(directory.resolve("conf.d"));
        Path fragment = directory.resolve("conf.d/tosser.conf");
        String content = "TossIntervalSeconds=15\n";
        Files.writeString(fragment, content);
        MailConfigurationStore store = new MailConfigurationStore(station);
        assertEquals(15, store.tosser().getIntervalSeconds());
        String original = Files.readString(path());
        assertThrows(IllegalArgumentException.class,
                     () -> store.saveTosser(new TosserConfiguration(30, true, true, false)));
        assertEquals(content, Files.readString(fragment));
        assertEquals(original, Files.readString(path()));
    }
}
