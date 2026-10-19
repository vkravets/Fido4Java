package org.fidonet.jftn.tosser;

import org.fidonet.types.FTNAddr;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** FTS-4009 tracking stamp, using the address and UTC layout emitted by HPT. */
final class NetmailVia {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss",
                                                                                   Locale.ROOT)
                                                                        .withZone(ZoneOffset.UTC);

    private NetmailVia() {
    }

    static String format(FTNAddr address, Instant time) {
        return "\1Via "
               + MessageIds.address(address)
               + " @"
               + TIMESTAMP.format(time)
               + ".UTC JFtn "
               + version(NetmailVia.class.getPackage().getImplementationVersion());
    }

    static String version(String value) {
        String version = value == null ? "dev" : value.replace("-SNAPSHOT", "-dev");
        return version.substring(0, Math.min(10, version.length()));
    }
}
