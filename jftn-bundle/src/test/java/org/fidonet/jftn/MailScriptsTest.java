package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class MailScriptsTest {
    @TempDir
    Path root;

    private JFtnConfig config(String script) throws Exception {
        String path = root.toString().replace('\\', '/');
        String text = "SysOp=Test\nInbound="
                      + path
                      + "/in\nOutbound="
                      + path
                      + "/out\nTmp="
                      + path
                      + "/state\nEchoPath="
                      + path
                      + "/echo\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\nJamBridgeEnabled=false\n"
                      + "MailScript="
                      + script.replace('\\', '/')
                      + "\n";
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private MailDatabaseFacade database() {
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:scripts"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        return base;
    }

    private Path packet(JFtnConfig config) throws Exception {
        Files.createDirectories(Path.of(config.getInbound()));
        Message message = new Message("Sysop",
                                      "Robot",
                                      new FTNAddr("1:500/101"),
                                      new FTNAddr("1:500/100"),
                                      "password",
                                      "Hello\r",
                                      new Date());
        Path packet = Path.of(config.getInbound(), "test.pkt");
        Files.write(packet,
                    PacketWriter.write(message.getFAddr(),
                                       message.getTAddr(),
                                       "secret",
                                       List.of(message)));
        return packet;
    }

    @Test
    void loadsScriptBeforeTossingRetriesFailuresAndUnloadsOnClose() throws Exception {
        Path script = root.resolve("robot.groovy");
        Files.writeString(script,
                          "" + "def register(api) {\n" + " api.onNetmail { message, peer, key ->\n"
                                  + "  if (api.state.get('attempt') == null) {\n"
                                  + "   api.state.put('attempt', '1'); throw new IOException('retry')\n"
                                  + "  }\n"
                                  + "  api.state.put('handled', peer.addr.toString()); true\n"
                                  + " }\n" + " api.onScan { api.state.put('scan', 'yes') }\n"
                                  + "}\n"
                                  + "def unload(api) { api.state.put('unloaded', 'yes') }\n");
        JFtnConfig config = config(script.toString());
        MailDatabaseFacade base = database();
        ScriptState state = new ScriptState(Path.of(config.getTmpDir(), "scripts"));
        try {
            Path packet = packet(config);
            try (MailRuntime service = new MailRuntime(config, base)) {
                service.processPending();
                assertTrue(Files.exists(packet));
                service.processPending();
                assertFalse(Files.exists(packet));
                assertEquals("1:500/101.0", state.get("handled"));
                assertEquals("yes", state.get("scan"));
                assertEquals(1, base.getLocalNetmail(new FTNAddr("1:500/100")).size());
            }
            assertEquals("yes", state.get("unloaded"));
        } finally {
            base.close();
        }
    }

    @Test
    void registrationFailureCleansHandlersAndReleasesServiceLock() throws Exception {
        Path script = root.resolve("bad.groovy");
        Files.writeString(script,
                          "def register(api) { api.onNetmail { m,p,k -> true }; throw new IOException('bad') }");
        JFtnConfig config = config(script.toString());
        MailDatabaseFacade base = database();
        try {
            assertThrows(IOException.class, () -> new MailRuntime(config, base));
            Files.writeString(script, "def register(api) {}\ndef unload(api) {}\n");
            try (MailRuntime service = new MailRuntime(config, base)) {
                service.processPending();
            }
        } finally {
            base.close();
        }
    }
}
