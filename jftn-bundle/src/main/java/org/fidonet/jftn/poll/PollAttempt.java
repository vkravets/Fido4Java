package org.fidonet.jftn.poll;

import java.util.List;

/** Transport-independent snapshot of an outgoing connection attempt. */
public final class PollAttempt {
    private final String id;
    private final String address;
    private String state;
    private final long created;
    private String detail;
    private String source = "MANUAL";
    private long started;
    private long finished;

    PollAttempt(String id, String address, String state, long created, String detail) {
        this.id = id;
        this.address = address;
        this.state = state;
        this.created = created;
        this.detail = detail;
    }

    public String getId() {
        return id;
    }
    public String getAddress() {
        return address;
    }
    public String getState() {
        return state;
    }
    public long getCreated() {
        return created;
    }
    public String getDetail() {
        return detail;
    }
    PollAttempt setState(String value) {
        state = value;
        return this;
    }
    PollAttempt setDetail(String value) {
        detail = value;
        return this;
    }
    public String getSource() {
        return source;
    }
    public long getStarted() {
        return started;
    }
    public long getFinished() {
        return finished;
    }
    PollAttempt source(String value) {
        source = value;
        return this;
    }
    PollAttempt started(long value) {
        started = value;
        return this;
    }
    PollAttempt finished(long value) {
        finished = value;
        return this;
    }
    public boolean isActive() {
        return List.of("QUEUED", "CONNECTING", "AUTHENTICATING", "TRANSFERRING").contains(state);
    }
    PollAttempt copy() {
        return new PollAttempt(id, address, state, created, detail).source(source)
                                                                   .started(started)
                                                                   .finished(finished);
    }
}
