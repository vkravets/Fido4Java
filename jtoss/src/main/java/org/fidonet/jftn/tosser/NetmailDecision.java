package org.fidonet.jftn.tosser;

import org.fidonet.types.Link;

/** A side-effect-free explanation; LOCAL succeeds without a next-hop connection. */
public final class NetmailDecision {
    public enum Reason {
        LOCAL, RULE, DIRECT, DEFAULT, NO_ROUTE, NO_PACK, DIRECT_UNAVAILABLE, INVALID_DESTINATION, TRANSIT_DISABLED, SOURCE_DENIED, TARGET_DENIED, RETURN_TO_SOURCE, VIA_LOOP
    }

    private final Reason reason;
    private final Link nextHop;
    private final String ruleId;

    NetmailDecision(Reason reason, Link nextHop, String ruleId) {
        this.reason = reason;
        this.nextHop = nextHop;
        this.ruleId = ruleId;
    }

    public Reason getReason() {
        return reason;
    }
    public Link getNextHop() {
        return nextHop;
    }
    public String getRuleId() {
        return ruleId;
    }
    public boolean isPermitted() {
        return reason == Reason.LOCAL || reason == Reason.NO_PACK || nextHop != null;
    }
}
