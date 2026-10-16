package org.fidonet.binkp.common;

/** Safe session metadata only; command payloads and authentication material are never supplied. */
@FunctionalInterface
public interface SessionActivityListener {
    enum Kind {
        STATE, SENT, RECEIVED
    }

    void record(Kind kind, String name, long bytes);
}
