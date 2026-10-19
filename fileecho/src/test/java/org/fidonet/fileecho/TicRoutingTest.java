package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

class TicRoutingTest {
    @Test
    void sortsSeenbyNumericallyAndRetainsPointsLikeHtick() {
        assertEquals(List.of("Seenby 1:500/2",
                             "Seenby 1:500/10",
                             "Seenby 1:500/10.1",
                             "Seenby 2:1/1"),
                     TicRouting.seen(Set.of(new FTNAddr("2:1/1"),
                                            new FTNAddr("1:500/10.1"),
                                            new FTNAddr("1:500/10"),
                                            new FTNAddr("1:500/2"))));
    }

    @Test
    void annotatesPathWithEpochAndHumanReadableUtcTime() {
        assertEquals("Path 1:500/100 1700000000 Tue Nov 14 22:13:20 2023 UTC JFtn fileecho",
                     TicRouting.path(new FTNAddr("1:500/100"), Instant.ofEpochSecond(1700000000)));
    }
}
