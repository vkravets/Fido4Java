package org.fidonet.jftn.tosser;

import org.fidonet.types.FTNAddr;

import java.util.regex.Pattern;

/** FTN wildcard masks, matched against complete 3D and 4D addresses. */
final class AddressPattern {
    private final Pattern regex;

    AddressPattern(String mask) {
        if (mask == null
                || !mask.matches("\\*|[0-9*?]+:\\*|[0-9*?]+:[0-9*?]+/[0-9*?]+(?:\\.[0-9*?]+)?")) {
            throw new IllegalArgumentException("Invalid FTN address mask: " + mask);
        }
        regex = Pattern.compile("\\Q"
                                + mask.replace("*", "\\E.*\\Q")
                                      .replace("?", "\\E.\\Q")
                                + "\\E");
    }

    boolean matches(FTNAddr address) {
        String value = address.toString();
        return regex.matcher(value).matches() || address.getPnt() == 0
                && regex.matcher(value.replaceFirst("\\.0$", "")).matches();
    }
}
