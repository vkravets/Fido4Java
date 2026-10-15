package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.IBase;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Explains fan-out and selects the local AKA without mutating stored messages. */
public final class EchoRouter {
    private final JFtnConfig config;
    private final IBase base;
    private final Map<String, FTNAddr> aliases = new HashMap<>();

    public EchoRouter(JFtnConfig config, IBase base) {
        this.config = config;
        this.base = base;
        databaseSubscriptions(config);
        List<String> values = config.getValuesAsList("EchoAka");
        if (values != null) {
            for (String value : values) {
                addAlias(value);
            }
        }
    }

    public static boolean databaseSubscriptions(JFtnConfig config) {
        String mode = config.getValue("EchoSubscriptions", "config");
        if (!mode.equalsIgnoreCase("config") && !mode.equalsIgnoreCase("database")) {
            throw new IllegalArgumentException("EchoSubscriptions must be config or database");
        }
        return mode.equalsIgnoreCase("database");
    }

    private void addAlias(String declaration) {
        String[] fields = declaration.split(",", -1);
        if (fields.length != 3 || fields[0].trim().isEmpty()) {
            throw new IllegalArgumentException("Expected EchoAka area,remote-link,local-AKA");
        }
        Link link = config.getLink(new FTNAddr(fields[1].trim()));
        FTNAddr local = new FTNAddr(fields[2].trim());
        if (link == null || link.getMyaddr() == null || !link.getMyaddr().contains(local)) {
            throw new IllegalArgumentException("Echo AKA must belong to the configured link");
        }
        if (aliases.putIfAbsent(key(fields[0].trim(), link), local) != null) {
            throw new IllegalArgumentException("Duplicate EchoAka binding");
        }
    }

    public List<EchoDecision> explain(Message message) {
        if (!message.isEchomail()
                || !base.isAreaExists(message.getArea().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Echo routing requires an existing echo area");
        }
        boolean archived = base.isArchived(message);
        List<Link> links = new ArrayList<>(config.getConfiguredLinks());
        links.sort(Comparator.comparing(link -> link.getAddr().toString()));
        List<EchoDecision> result = new ArrayList<>();
        for (Link link : links) {
            EchoDecision.Reason reason = archived
                    ? EchoDecision.Reason.ARCHIVED
                    : !base.canReadEcho(link, message.getArea())
                            ? EchoDecision.Reason.NO_READ_ACCESS
                            : reason(message, link);
            result.add(new EchoDecision(link, reason));
        }
        return result;
    }

    List<Link> recipients(Message message, List<Link> readers) {
        List<Link> result = new ArrayList<>();
        for (Link link : readers) {
            if (reason(message, link) == EchoDecision.Reason.ELIGIBLE) {
                result.add(link);
            }
        }
        return result;
    }

    private EchoDecision.Reason reason(Message message, Link link) {
        if (config.getLocalAddresses().contains(link.getAddr())) {
            return EchoDecision.Reason.LOCAL_LINK;
        }
        if (link.getAddr().equals(message.getUpLink())) {
            return EchoDecision.Reason.INPUT_LINK;
        }
        if (link.getAddr().getPnt() == 0
                && message.getBody().getSeenBy().getAddresses().contains(link.getAddr())) {
            return EchoDecision.Reason.SEEN_BY;
        }
        return EchoDecision.Reason.ELIGIBLE;
    }

    public FTNAddr localAddress(String area, Link link) {
        FTNAddr selected = aliases.get(key(area, link));
        if (selected != null) {
            return selected;
        }
        if (link.getMyaddr() == null || link.getMyaddr().isEmpty()) {
            throw new IllegalArgumentException("Missing local echo AKA for " + link.getAddr());
        }
        return link.getMyaddr().get(0);
    }

    private String key(String area, Link link) {
        return area.toLowerCase(Locale.ROOT) + "\0" + link.getAddr();
    }
}
