package org.fidonet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.echobase.MessageIteration;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

public class MessageIterationTest {
    @Test
    public void closesQueryAfterSuccessfulIteration() throws Exception {
        Query query = new Query(false);
        int[] processed = {0};
        MessageIteration.forEach(query, message -> processed[0]++);
        assertEquals(1, processed[0]);
        assertTrue(query.closed);
    }

    @Test
    public void retainsProcessingFailureWhenClosingAlsoFails() throws Exception {
        Query query = new Query(true);
        IOException failure = new IOException("processing failure");
        try {
            MessageIteration.forEach(query, message -> {
                throw failure;
            });
            fail("Expected processing failure");
        } catch (IOException caught) {
            assertSame(failure, caught);
            assertEquals(1, caught.getSuppressed().length);
            assertTrue(query.closed);
        }
    }

    @Test
    public void acceptsIteratorsWithoutCloseAndReportsStandaloneCloseFailure() throws Exception {
        MessageIteration.forEach(Collections.emptyIterator(), message -> fail("Empty query"));
        try {
            MessageIteration.forEach(new Query(true), message -> {
            });
            fail("Expected close failure");
        } catch (IOException failure) {
            assertEquals("close failure", failure.getCause().getMessage());
        }
    }

    private static final class Query implements Iterator<Message>, AutoCloseable {
        private final boolean failClose;
        private boolean consumed;
        private boolean closed;

        private Query(boolean failClose) {
            this.failClose = failClose;
        }

        @Override
        public boolean hasNext() {
            return !consumed;
        }

        @Override
        public Message next() {
            consumed = true;
            return null;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failClose) {
                throw new IOException("close failure");
            }
        }
    }
}
