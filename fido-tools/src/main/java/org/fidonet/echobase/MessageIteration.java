package org.fidonet.echobase;

import org.fidonet.types.Message;

import java.io.IOException;
import java.util.Iterator;

/** Traverses a message query and closes it, retaining the original processing failure. */
public final class MessageIteration {
    private MessageIteration() {
    }

    public static void forEach(Iterator<Message> messages, Handler handler) throws IOException {
        try (QueryResource resource = new QueryResource(messages)) {
            while (messages.hasNext()) {
                handler.accept(messages.next());
            }
        }
    }

    public interface Handler {
        void accept(Message message) throws IOException;
    }

    private static final class QueryResource implements AutoCloseable {
        private final Iterator<Message> messages;

        private QueryResource(Iterator<Message> messages) {
            this.messages = messages;
        }

        @Override
        public void close() throws IOException {
            if (!(messages instanceof AutoCloseable)) {
                return;
            }
            try {
                ((AutoCloseable) messages).close();
            } catch (Exception e) {
                throw new IOException("Unable to close message iterator", e);
            }
        }
    }
}
