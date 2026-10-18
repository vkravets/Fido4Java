package org.fidonet.binkp.common.codec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionTestFixtures;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.io.BinkFrame;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class BinkpLogTest {
    private final Logger logger = (Logger) LogManager.getLogger(BinkpLog.class);
    private final Capture capture = new Capture();
    private Level previous;

    @BeforeEach
    void attach() {
        LoggerFactory.getILoggerFactory();
        previous = logger.getLevel();
        capture.start();
        logger.addAppender(capture);
        Configurator.setLevel(logger.getName(), Level.INFO);
    }

    @AfterEach
    void detach() {
        logger.removeAppender(capture);
        Configurator.setLevel(logger.getName(), previous);
        capture.stop();
    }

    @Test
    void logsBothDirectionsAtInfoAndRedactsPlaintextAndCramPasswords() {
        BinkpLog.command(null, "connection-1", true, command(BinkCommand.M_PWD, "secret"));
        BinkpLog.command(null,
                         "connection-1",
                         false,
                         command(BinkCommand.M_PWD, "CRAM-MD5-deadbeef"));
        assertEquals(2, capture.lines.size());
        assertEquals("binkp <<< [connection-1] PWD [redacted]", capture.lines.get(0));
        assertEquals("binkp >>> [connection-1] PWD [redacted]", capture.lines.get(1));
        assertFalse(capture.lines.toString().contains("secret"));
        assertFalse(capture.lines.toString().contains("deadbeef"));
    }

    @Test
    void logsCommandsWithoutFileContentsAndWithoutMutatingFrames() {
        BinkFrame file = command(BinkCommand.M_FILE, "mail.pkt 100 1700000000 -1");
        BinkpLog.command(null, "connection-1", false, file);
        BinkpLog.command(null, "connection-1", true, command(BinkCommand.M_EOB, ""));
        byte[] contents = "private message body".getBytes(StandardCharsets.UTF_8);
        BinkpLog.command(null,
                         "connection-1",
                         false,
                         new BinkFrame((short) contents.length, contents));
        BinkpLog.command(null, "connection-1", true, new BinkFrame((short) 0x8000, new byte[0]));
        assertEquals(List.of("binkp >>> [connection-1] FILE mail.pkt 100 1700000000 -1",
                             "binkp <<< [connection-1] EOB"),
                     capture.lines);
        assertEquals("FILE mail.pkt 100 1700000000 -1", BinkpLog.describe(file));
    }

    @Test
    void escapesControlCharactersAndBoundsRemoteText() {
        BinkpLog.command(null,
                         "peer\r\n",
                         true,
                         command(BinkCommand.M_NUL, "SYS name\nforged\u001b\u2028"));
        assertEquals("binkp <<< [peer\\u000d\\u000a] NUL SYS name\\u000aforged\\u001b\\u2028",
                     capture.lines.get(0));
        String bounded = BinkpLog.text("x".repeat(3000));
        assertEquals(2048 + " [truncated]".length(), bounded.length());
        assertTrue(bounded.endsWith(" [truncated]"));
    }

    @Test
    void omitsUnknownCommandPayload() {
        BinkFrame frame = command(BinkCommand.M_PWD, "unknown secret");
        frame.getData()[0] = (byte) 127;
        BinkpLog.command(null, "connection-1", true, frame);
        assertEquals("binkp <<< [connection-1] UNKNOWN(127) [payload omitted]",
                     capture.lines.get(0));
    }

    @Test
    void journalsStayIsolatedAndDoNotDependOnTheFileLoggerLevel() {
        SessionContext first = SessionTestFixtures.loopbackContext("1:500/100",
                                                                   "1:500/101",
                                                                   "secret",
                                                                   0);
        SessionContext second = new SessionContext(first);
        List<String> firstLines = new ArrayList<>();
        List<String> secondLines = new ArrayList<>();
        first.setProtocolLogListener(firstLines::add);
        second.setProtocolLogListener(secondLines::add);
        Configurator.setLevel(logger.getName(), Level.ERROR);
        BinkpLog.command(first, "first", true, command(BinkCommand.M_PWD, "secret"));
        BinkpLog.command(second, "second", false, command(BinkCommand.M_ADR, "1:500/102"));
        assertEquals(List.of("binkp <<< [first] PWD [redacted]"), firstLines);
        assertEquals(List.of("binkp >>> [second] ADR 1:500/102"), secondLines);
        new SessionContext(first).recordProtocolLog("not inherited");
        assertEquals(1, firstLines.size());
        first.setProtocolLogListener(line -> {
            throw new IllegalStateException("journal failure");
        });
        assertDoesNotThrow(() -> BinkpLog.command(first,
                                                  "first",
                                                  true,
                                                  command(BinkCommand.M_EOB, "")));
    }

    private static BinkFrame command(BinkCommand command, String arguments) {
        byte[] data = (" " + arguments).getBytes(StandardCharsets.UTF_8);
        data[0] = command.getCmd();
        return new BinkFrame((short) (0x8000 | data.length), data);
    }

    private static final class Capture extends AbstractAppender {
        final List<String> lines = new ArrayList<>();

        Capture() {
            super("binkp-test", null, PatternLayout.createDefaultLayout(), false,
                    Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            assertEquals(Level.INFO, event.getLevel());
            lines.add(event.getMessage().getFormattedMessage());
        }
    }
}
