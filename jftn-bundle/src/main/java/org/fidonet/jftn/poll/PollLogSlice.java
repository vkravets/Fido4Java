package org.fidonet.jftn.poll;

import java.util.List;

/** Bounded cursor page of one immutable-identity attempt journal. */
public final class PollLogSlice {
    private final List<String> lines;
    private final long nextOffset;
    private final boolean hasMore;

    PollLogSlice(List<String> lines, long nextOffset, boolean hasMore) {
        this.lines = List.copyOf(lines);
        this.nextOffset = nextOffset;
        this.hasMore = hasMore;
    }
    public List<String> getLines() {
        return lines;
    }
    public long getNextOffset() {
        return nextOffset;
    }
    public boolean isHasMore() {
        return hasMore;
    }
}
