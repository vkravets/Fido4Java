package org.fidonet.fileecho;

import org.fidonet.types.FTNAddr;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Canonical HTick address ordering and UTC path annotations. */
final class TicRouting {
    private static final Comparator<FTNAddr> ORDER = Comparator.comparing(FTNAddr::getZone)
                                                               .thenComparing(FTNAddr::getNet)
                                                               .thenComparing(FTNAddr::getNode)
                                                               .thenComparing(FTNAddr::getPnt);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy 'UTC'",
                                                                              Locale.ENGLISH)
                                                                   .withZone(ZoneOffset.UTC);

    private TicRouting() {
    }

    static String address(FTNAddr address) {
        String node = address.getZone() + ":" + address.getNet() + "/" + address.getNode();
        return address.getPnt() == 0 ? node : node + "." + address.getPnt();
    }

    static String path(FTNAddr address, Instant time) {
        return "Path "
               + address(address)
               + " "
               + time.getEpochSecond()
               + " "
               + TIME.format(time)
               + " JFtn fileecho";
    }

    static List<String> seen(Set<FTNAddr> addresses) {
        return addresses.stream()
                        .sorted(ORDER)
                        .map(value -> "Seenby " + address(value))
                        .collect(Collectors.toList());
    }
}
