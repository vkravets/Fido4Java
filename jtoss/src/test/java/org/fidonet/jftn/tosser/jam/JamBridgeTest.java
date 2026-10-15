package org.fidonet.jftn.tosser.jam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class JamBridgeTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private DatabaseManager base;
    private JFtnConfig config;
    private JamBridge bridge;
    private JamArea echo;
    private JamArea netmail;
    private Path root;

    @Before
    public void open() throws Exception {
        root = temporary.getRoot().toPath();
        String directory = root.toString().replace('\\', '/');
        String text = "Inbound="
                      + directory
                      + "/inbound\nTmp="
                      + directory
                      + "/state\n"
                      + "Outbound="
                      + directory
                      + "/outbound\nEchoPath="
                      + directory
                      + "/echo\nSysOp=Test\n"
                      + "Link=2:500/101,2:500/102,secret,localhost\nDefaultRoute=2:500/101\n"
                      + "JamEcho=TEST,"
                      + directory
                      + "/jam/test\nJamNetmail="
                      + directory
                      + "/jam/netmail\n";
        config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        base = new DatabaseManager(new OrmManager("jdbc:h2:mem:jam" + System.nanoTime()));
        assertTrue(base.open());
        assertTrue(base.createArea("test"));
        bridge = new JamBridge(config, base, base);
        echo = new JamArea(root.resolve("jam/test"), "test", new FTNAddr("2:500/102"));
        netmail = new JamArea(root.resolve("jam/netmail"), null, new FTNAddr("2:500/102"));
    }

    @After
    public void close() {
        base.close();
    }

    private Message message(boolean echomail) {
        return new Message("Remote",
                           "Sysop",
                           new FTNAddr("2:500/101"),
                           new FTNAddr("2:500/102"),
                           "Тема",
                           (echomail ? "AREA:TEST\r" : "")
                           + "\1MSGID: 2:500/101 abc\r\1CHRS: UTF-8 4\rПривет\r",
                           new Date(1700000000000L));
    }

    @Test
    public void exportsEchoAndLocalNetmailAcrossRestartWithoutDuplicates() throws Exception {
        base.addMessage(message(true), "test");
        base.saveNetmail(message(false), "inbound", null);
        bridge.exportMessages();
        new JamBridge(config, base, base).exportMessages();
        assertEquals(1, echo.read().size());
        assertEquals(1, netmail.read().size());
        assertEquals("Тема", echo.read().get(0).getMessage().getSubject());
        assertFalse((echo.read().get(0).getAttributes() & JamArea.LOCAL) != 0);
    }

    @Test
    public void preservesLastReadsAndDoesNotResurrectEditorDeletedMessages() throws Exception {
        base.addMessage(message(true), "test");
        bridge.exportMessages();
        Path lastread = Path.of(echo.getPath() + ".jlr");
        Files.write(lastread, new byte[16]);
        echo.locked(access -> {
            access.attributes(1, JamArea.DELETED, 0);
            return null;
        });
        bridge.exportMessages();
        assertTrue(echo.read().isEmpty());
        assertEquals(16, Files.size(lastread));
        assertEquals(1, base.getMessageSize("test"));
    }
}
