package org.fidonet.nodelist;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** FTS-5001 IBN endpoints. Does not resolve DNS or grant permission to connect. */
public final class InternetEndpoint {
    private final String host;
    private final int port;

    private InternetEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    static List<InternetEndpoint> binkp(DirectoryEntry entry) {
        List<String> flags = DirectoryFlags.normalized(entry.getFlags());
        List<String> defaults = new ArrayList<>();
        for (String flag : flags) {
            if (flag.toUpperCase(Locale.ROOT).startsWith("INA:")) {
                defaults.add(flag.substring(4));
            }
        }
        if (defaults.isEmpty()) {
            for (String flag : flags) {
                if (flag.toUpperCase(Locale.ROOT).matches("(?:IBN|IFC|IFT|ITN|IVM|IP):.+")) {
                    String value = flag.substring(flag.indexOf(':') + 1);
                    if (!value.matches("\\d+")) {
                        defaults.add(hostPart(value));
                    }
                }
            }
        }
        if (defaults.isEmpty() && isHost(entry.getSystem())) {
            defaults.add(entry.getSystem());
        }
        if (defaults.isEmpty() && entry.getPhone().matches("000(?:-\\d{1,3}){4}")) {
            defaults.add(entry.getPhone().substring(4).replace('-', '.'));
        }
        List<InternetEndpoint> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String flag : flags) {
            if (!flag.equalsIgnoreCase("IBN")
                    && !flag.toUpperCase(Locale.ROOT).startsWith("IBN:")) {
                continue;
            }
            String value = flag.length() > 3 ? flag.substring(4) : "";
            if (value.isEmpty() || value.matches("\\d+")) {
                int port = value.isEmpty() ? 24554 : port(value);
                for (String host : defaults) {
                    add(result, seen, host, port);
                }
            } else {
                int end = value.startsWith("[") ? value.indexOf(']') + 1 : value.indexOf(':');
                if (end == 0) {
                    throw new IllegalArgumentException("Unterminated IPv6 address: " + flag);
                }
                String host = end < 0 ? value : value.substring(0, end);
                String suffix = end < 0 ? "" : value.substring(end);
                int port = suffix.isEmpty() ? 24554 : port(suffix.substring(1));
                if (!suffix.isEmpty() && suffix.charAt(0) != ':') {
                    throw new IllegalArgumentException("Invalid IBN endpoint: " + flag);
                }
                add(result, seen, host, port);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isHost(String value) {
        return !value.contains("_") && !value.contains(" ") && !value.contains("@")
                && (value.contains(".") || value.startsWith("["));
    }

    private static String hostPart(String value) {
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) {
                throw new IllegalArgumentException("Unterminated IPv6 address: " + value);
            }
            return value.substring(0, end + 1);
        }
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(0, colon);
    }

    private static int port(String value) {
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid BinkP port: " + value);
        }
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid BinkP port: " + value);
        }
        return port;
    }

    private static void add(List<InternetEndpoint> result,
                            Set<String> seen,
                            String host,
                            int port) {
        if (host.isEmpty() || host.contains(" ") || host.contains("@") || host.contains("_")) {
            throw new IllegalArgumentException("Invalid Internet host: " + host);
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
            validateIpv6(host);
        } else if (host.contains(":")) {
            throw new IllegalArgumentException("IPv6 literals must use brackets");
        } else {
            validateHost(host);
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Empty Internet host");
        }
        if (seen.add(host.toLowerCase(Locale.ROOT) + ":" + port)) {
            result.add(new InternetEndpoint(host, port));
        }
    }

    private static void validateIpv6(String host) {
        if (!host.contains(":") || !host.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("Invalid IPv6 literal: " + host);
        }
        try {
            InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 literal: " + host, e);
        }
    }

    private static void validateHost(String host) {
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty() || host.length() > 253) {
            throw new IllegalArgumentException("Invalid Internet host: " + host);
        }
        for (String label : host.split("\\.", -1)) {
            if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) {
                throw new IllegalArgumentException("Invalid Internet host: " + host);
            }
        }
        if (host.matches("(?:\\d+\\.){3}\\d+")) {
            for (String octet : host.split("\\.")) {
                if (Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 literal: " + host);
                }
            }
        }
    }
}
