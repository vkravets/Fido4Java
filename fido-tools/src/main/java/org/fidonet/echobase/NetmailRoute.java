package org.fidonet.echobase;

import org.fidonet.types.FTNAddr;

/** An ordered operator-defined rule, independent of individual delivery records. */
public final class NetmailRoute {
    private final String id;
    private final int priority;
    private final String pattern;
    private final String nextHop;
    private final boolean enabled;

    public NetmailRoute(String id, int priority, String pattern, String nextHop, boolean enabled) {
        if (id == null || id.trim().isEmpty() || pattern == null || pattern.trim().isEmpty()
                || nextHop == null || nextHop.trim().isEmpty()) {
            throw new IllegalArgumentException("Route ID and destination pattern are required");
        }
        FTNAddr address = new FTNAddr(nextHop);
        if (!address.isValid()) {
            throw new IllegalArgumentException("Invalid next hop: " + nextHop);
        }
        this.id = id;
        this.priority = priority;
        this.pattern = pattern.trim();
        this.nextHop = address.toString();
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }
    public int getPriority() {
        return priority;
    }
    public String getPattern() {
        return pattern;
    }
    public String getNextHop() {
        return nextHop;
    }
    public boolean isEnabled() {
        return enabled;
    }
}
