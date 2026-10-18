package org.fidonet.binkp.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures the actual production INFO messages, independently of the protocol test probe. */
final class InfoLog extends AbstractAppender implements AutoCloseable {
    private final Logger logger = (Logger) LogManager.getLogger("org.fidonet.binkp");
    private final Level previous = logger.getLevel();
    private final List<String> lines = new CopyOnWriteArrayList<>();

    InfoLog() {
        super("binkp-info", null,
                PatternLayout.newBuilder().withPattern("%d{ISO8601} %-5p %c{1} - %m%n").build(),
                false, Property.EMPTY_ARRAY);
        LoggerFactory.getILoggerFactory();
        start();
        logger.addAppender(this);
        Configurator.setLevel(logger.getName(), Level.INFO);
    }

    @Override
    public void append(LogEvent event) {
        if (event.getLevel() == Level.INFO) {
            lines.add(getLayout().toSerializable(event).toString());
        }
    }

    void assertExchange() {
        String text = String.join("", lines);
        assertTrue(text.contains("binkp <<< ["));
        assertTrue(text.contains("binkp >>> ["));
        assertTrue(text.contains("PWD [redacted]"));
        assertFalse(text.contains("docker-secret"));
        assertTrue(text.contains("binkp sending:"));
        assertTrue(text.contains("binkp receiving:"));
        assertEquals(2,
                     lines.stream()
                          .filter(line -> line.contains("binkp file sent and acknowledged:"))
                          .count());
        assertEquals(1,
                     lines.stream().filter(line -> line.contains("binkp file received:")).count());
        assertTrue(text.contains("transferred=17009"));
        assertTrue(text.contains("CPS="));
    }

    void save(Path directory) throws IOException {
        Files.writeString(directory.resolve("jftn-info.log"), String.join("", lines));
    }

    @Override
    public void close() {
        logger.removeAppender(this);
        Configurator.setLevel(logger.getName(), previous);
        stop();
    }
}
