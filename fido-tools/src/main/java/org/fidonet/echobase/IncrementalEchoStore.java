package org.fidonet.echobase;

import java.io.IOException;

/** Advances each consumer only after its durable processing of a message succeeds. */
public interface IncrementalEchoStore {
    void visitNewEcho(String area, String consumer, MessageIteration.Handler handler)
            throws IOException;
}
