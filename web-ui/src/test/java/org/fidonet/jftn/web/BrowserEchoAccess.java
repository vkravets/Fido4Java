package org.fidonet.jftn.web;

import org.fidonet.jftn.rpc.EchoAccessConfiguration;
import org.fidonet.jftn.rpc.EchoSubscriptionInfo;
import org.fidonet.jftn.rpc.LogPage;
import org.fidonet.jftn.rpc.PollPage;
import org.fidonet.jftn.rpc.UiService;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Isolated browser interaction state; SQL and tosser semantics have runner RPC tests. */
final class BrowserEchoAccess {
    private final BrowserQuarantine quarantine = new BrowserQuarantine();
    private final Map<String, EchoSubscriptionInfo> subscriptions = new TreeMap<>();
    private final TreeSet<String> areas = new TreeSet<>();

    UiService.Iface handler() {
        return (UiService.Iface) Proxy.newProxyInstance(UiService.Iface.class.getClassLoader(),
                                                        new Class<?>[]{UiService.Iface.class},
                                                        (proxy,
                                                         method,
                                                         args) -> dispatch(method.getName(), args));
    }

    private synchronized Object dispatch(String method, Object[] args) {
        switch (method) {
            case "pollQueue" :
            case "links" :
                return List.of();
            case "pollHistory" :
                return new PollPage(List.of(), "", false);
            case "logs" :
                return new LogPage(((Long) args[1]) == -1
                        ? List.of("INFO binkp session ready: peer=1:500/101, role=SERVER, NR=true",
                                  "INFO binkp <<< [id=incoming] PWD [redacted]",
                                  "INFO binkp >>> [id=outgoing] EOB")
                        : List.of(), 100, false);
            case "echoAccess" :
                List<EchoSubscriptionInfo> rows = new ArrayList<>();
                subscriptions.values().forEach(row -> rows.add(row.deepCopy()));
                return new EchoAccessConfiguration("database",
                                                   List.of("1:500/101.0"),
                                                   new ArrayList<>(areas),
                                                   rows);
            case "subscribe" :
                String area = ((String) args[2]).toLowerCase(Locale.ROOT);
                areas.add(area);
                subscriptions.put(args[1] + ":"
                        + area, new EchoSubscriptionInfo((String) args[1], area, (String) args[3]));
                return null;
            case "unsubscribe" :
                subscriptions.remove(args[1] + ":" + args[2]);
                return null;
            default :
                return quarantine.dispatch(method, args);
        }
    }
}
