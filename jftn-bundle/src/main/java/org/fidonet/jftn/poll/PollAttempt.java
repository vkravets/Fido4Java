package org.fidonet.jftn.poll;

/** Transport-independent snapshot of an outgoing connection attempt. */
public final class PollAttempt {
    private final String id;
    private final String address;
    private String state;
    private final long created;
    private String detail;

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
    PollAttempt copy() {
        return new PollAttempt(id, address, state, created, detail);
    }
}
