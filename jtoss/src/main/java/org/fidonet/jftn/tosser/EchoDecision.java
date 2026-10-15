package org.fidonet.jftn.tosser;

import org.fidonet.types.Link;

/** Eligibility of a configured echo peer for one message. */
public final class EchoDecision {
    public enum Reason {
        ELIGIBLE, NO_READ_ACCESS, ARCHIVED, INPUT_LINK, SEEN_BY, LOCAL_LINK
    }
    private final Link link;
    private final Reason reason;

    EchoDecision(Link link, Reason reason) {
        this.link = link;
        this.reason = reason;
    }

    public Link getLink() {
        return link;
    }
    public Reason getReason() {
        return reason;
    }
    public boolean isEligible() {
        return reason == Reason.ELIGIBLE;
    }
}
