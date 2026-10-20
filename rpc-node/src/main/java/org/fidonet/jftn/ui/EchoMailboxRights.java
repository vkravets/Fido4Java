package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.MailRepository;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** One request's rights snapshot; never cached across account or subscription changes. */
final class EchoMailboxRights {
    private final boolean administrator;
    private final List<String> authors;
    private final Set<String> readable = new HashSet<>();
    private final Map<String, List<String>> writers = new HashMap<>();

    EchoMailboxRights(boolean administrator,
                      List<FTNAddr> addresses,
                      JFtnConfig config,
                      MailRepository base) {
        this.administrator = administrator;
        authors = addresses.stream().map(FTNAddr::toString).collect(Collectors.toList());
        if (administrator)
            return;
        for (FTNAddr address : addresses) {
            Link link = config.getLink(address);
            if (link == null)
                continue;
            base.getEchoAccess(link).forEach((area, level) -> grant(area, address, level));
        }
    }

    private void grant(String area, FTNAddr address, AccessLevel level) {
        if (level == AccessLevel.READ || level == AccessLevel.BOTH)
            readable.add(area);
        if (level == AccessLevel.WRITE || level == AccessLevel.BOTH) {
            writers.computeIfAbsent(area, ignored -> new ArrayList<>()).add(address.toString());
        }
    }

    boolean readable(String area) {
        return administrator || readable.contains(area);
    }

    List<String> authors(String area) {
        return administrator
                ? List.copyOf(authors)
                : List.copyOf(writers.getOrDefault(area, List.of()));
    }
}
