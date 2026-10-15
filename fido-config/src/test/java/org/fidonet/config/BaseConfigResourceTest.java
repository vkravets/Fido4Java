package org.fidonet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BaseConfigResourceTest {
    private BaseConfig config() {
        return new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
    }

    @Test
    public void closesInputAfterSuccessfulLoad() throws Exception {
        TrackingStream stream = new TrackingStream("key=value\n");
        BaseConfig config = config();
        config.load(stream);
        assertEquals("value", config.getValue("key"));
        assertTrue(stream.closed);
    }

    @Test
    public void closesInputWhenValueIsEmpty() throws Exception {
        TrackingStream stream = new TrackingStream("key=\n");
        BaseConfig config = config();
        config.load(stream);
        assertNull(config.getValue("key"));
        assertTrue(stream.closed);
    }

    @Test
    public void preservesEqualsInValuesAndContinuesAfterEmptyValues() throws Exception {
        BaseConfig config = config();
        config.load(new TrackingStream("password=a=b==\nempty=\nnext=value\n"));
        assertEquals("a=b==", config.getValue("password"));
        assertNull(config.getValue("empty"));
        assertEquals("value", config.getValue("next"));
    }

    @Test
    public void closesInputWhenReadingThrows() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failure");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        try {
            config().load(stream);
            fail("Expected read failure");
        } catch (ParseConfigException expected) {
            assertTrue(closed.get());
            assertEquals("read failure", expected.getCause().getMessage());
        }
    }

    @Test
    public void keyLookupDoesNotDependOnDefaultLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            BaseConfig config = config();
            config.load(new TrackingStream("INBOUND=path\n"));
            assertEquals("path", config.getValue("INBOUND"));
            assertEquals("path", config.getValue("inbound"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static class TrackingStream extends ByteArrayInputStream {
        boolean closed;

        TrackingStream(String input) {
            super(input.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    @Test
    public void ignoresWhitespaceAndEmptyKeysAndReturnsIndependentValueLists() throws Exception {
        BaseConfig config = config();
        config.load(new TrackingStream(" \t \n# comment\n =ignored\nKEY=first # tail\nkey=second\n"));
        assertEquals("first", config.getValue(" key "));
        List<String> values = config.getValuesAsList("KEY");
        assertEquals(Arrays.asList("first", "second"), values);
        values.clear();
        assertEquals(2, config.getValuesAsList("key").size());
        assertEquals("fallback", config.getValue("missing", "fallback"));
        assertNull(config.getValue(""));
    }

    @Test
    public void malformedLineIsReportedRatherThanSilentlyTruncatingConfiguration()
            throws Exception {
        TrackingStream input = new TrackingStream("first=ok\ninvalid line\nnext=value\n");
        try {
            config().load(input);
            fail("Malformed line was silently accepted");
        } catch (ParseConfigException expected) {
            assertTrue(expected.getMessage().contains("2"));
            assertTrue(input.closed);
        }
    }
}
