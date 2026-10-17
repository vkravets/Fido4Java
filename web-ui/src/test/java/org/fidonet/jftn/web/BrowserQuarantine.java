package org.fidonet.jftn.web;

import org.fidonet.jftn.rpc.QuarantineItem;
import org.fidonet.jftn.rpc.QuarantinePage;
import org.fidonet.jftn.rpc.QuarantineRetryResult;

import java.util.ArrayList;
import java.util.List;

/** UI fixture only; actual packet processing and journal recovery have core/RPC tests. */
final class BrowserQuarantine {
    private final List<QuarantineItem> items = new ArrayList<>(List.of(new QuarantineItem("a".repeat(64),
                                                                                          "legacy.bad",
                                                                                          100,
                                                                                          0,
                                                                                          "b".repeat(64),
                                                                                          "",
                                                                                          "No recorded reason (legacy packet).",
                                                                                          0,
                                                                                          0)));

    Object dispatch(String method, Object[] args) {
        switch (method) {
            case "quarantine" :
                return new QuarantinePage(new ArrayList<>(items),
                                          "",
                                          false,
                                          List.of("1:500/101.0"));
            case "retryQuarantine" :
                if (!"1:500/101.0".equals(args[3])) {
                    throw new IllegalArgumentException("Select an expected source link.");
                }
                items.removeIf(item -> item.getId().equals(args[1])
                        && item.getChecksum().equals(args[2]));
                return new QuarantineRetryResult(true,
                                                 "Packet accepted. Original retained in recovery archive.");
            case "archiveQuarantine" :
                items.removeIf(item -> item.getId().equals(args[1])
                        && item.getChecksum().equals(args[2]));
                return null;
            default :
                throw new UnsupportedOperationException("Not provided by the browser fixture");
        }
    }
}
