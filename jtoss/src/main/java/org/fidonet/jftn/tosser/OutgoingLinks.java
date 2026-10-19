package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ServiceLoader;

public final class OutgoingLinks {
    private OutgoingLinks() {
    }

    public static boolean virtualEnabled(JFtnConfig config) {
        String value = config.getValue("AllowVirtualOutbound", "false");
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("AllowVirtualOutbound must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public static Link find(JFtnConfig config, FTNAddr address) {
        Link configured = config.getLink(address);
        if (configured != null || !virtualEnabled(config)
                || config.getLocalAddresses().contains(address)) {
            return configured;
        }
        try {
            for (OutgoingLinkResolver resolver : ServiceLoader.load(OutgoingLinkResolver.class)) {
                Link link = resolver.lookup(config, address);
                if (link != null) {
                    return link;
                }
            }
            return null;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot resolve outgoing directory link", failure);
        }
    }
}
