package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class NetmailViaTest {
    @Test
    void usesUtcAndHptAddressLayoutForNodesAndPoints() {
        Instant time = Instant.parse("2026-10-19T09:08:07Z");
        String node = NetmailVia.format(new FTNAddr("1:500/100.0"), time);
        String point = NetmailVia.format(new FTNAddr("1:500/100.7"), time);
        assertTrue(node.startsWith("\1Via 1:500/100 @20261019.090807.UTC JFtn "));
        assertTrue(point.startsWith("\1Via 1:500/100.7 @20261019.090807.UTC JFtn "));
        assertTrue(node.matches("\\x01Via \\S+ @\\d{8}\\.\\d{6}\\.UTC JFtn \\S{1,10}"));
    }

    @Test
    void identifiesTheActualBuildWithinTheFts4009VersionFieldLimit() {
        assertEquals("dev", NetmailVia.version(null));
        assertEquals("1.0-dev", NetmailVia.version("1.0-SNAPSHOT"));
        assertEquals("2.3.4", NetmailVia.version("2.3.4"));
        assertEquals(10, NetmailVia.version("2.3.4-long-development-build").length());
    }
}
