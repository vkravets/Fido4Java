package org.fidonet.jftn.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.MailLinkConfiguration;
import org.fidonet.jftn.rpc.TosserConfiguration;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Operational mail settings share the station store's atomic, conflict-aware writer. */
final class MailConfigurationStore {
    private final NodeConfigurationStore store;

    MailConfigurationStore(NodeConfigurationStore store) {
        this.store = store;
    }

    List<MailLinkConfiguration> links() throws Exception {
        JFtnConfig config = store.configuration();
        Map<String, String> packets = packetPasswords(config);
        List<MailLinkConfiguration> result = new ArrayList<>();
        for (String entry : config.getValuesAsList("Link")) {
            Link link = new Link(entry);
            String packet = packets.get(link.getAddr().toString());
            result.add(new MailLinkConfiguration(link.getAddr().toString(),
                                                 link.getMyaddr()
                                                     .stream()
                                                     .map(Object::toString)
                                                     .collect(Collectors.toList()),
                                                 link.getHostAddress() == null
                                                         ? ""
                                                         : link.getHostAddress(),
                                                 link.getPort() == 0 ? 24554 : link.getPort(),
                                                 "",
                                                 "",
                                                 packetMode(packet)));
        }
        return result;
    }

    private String packetMode(String password) {
        if (password == null) {
            return "INHERIT";
        }
        return password.equals("-") || password.isEmpty() ? "UNPROTECTED" : "SEPARATE";
    }

    void saveLinks(List<MailLinkConfiguration> values) throws Exception {
        if (values == null || values.isEmpty() || values.size() > 1024) {
            throw new IllegalArgumentException("Configure between 1 and 1024 links");
        }
        JFtnConfig config = store.configuration();
        Map<String, String> packets = packetPasswords(config);
        Set<String> addresses = new LinkedHashSet<>();
        List<String> links = new ArrayList<>();
        for (MailLinkConfiguration value : values) {
            links.add(link(value, config, packets, addresses));
        }
        for (Link existing : config.getConfiguredLinks()) {
            if (!addresses.contains(existing.getAddr().toString())) {
                throw new IllegalArgumentException("Link removal requires reviewing routes and subscriptions in the configuration file");
            }
        }
        String primary = new Link(config.getValue("Link")).getAddr().toString();
        if (!addresses.iterator().next().equals(primary)) {
            throw new IllegalArgumentException("Keep the primary link first");
        }
        Map<String, List<String>> changes = new LinkedHashMap<>();
        changes.put("link", links);
        changes.put("packetpassword",
                    packets.entrySet()
                           .stream()
                           .map(entry -> entry.getKey() + "," + entry.getValue())
                           .collect(Collectors.toList()));
        store.replaceOptions(changes);
    }

    private String link(MailLinkConfiguration value,
                        JFtnConfig config,
                        Map<String, String> packets,
                        Set<String> addresses) {
        if (value == null) {
            throw new IllegalArgumentException("Link is required");
        }
        String address = AccountDirectory.address(value.getAddress(), false);
        if (!addresses.add(address)) {
            throw new IllegalArgumentException("Duplicate link address");
        }
        List<String> local = localAddresses(value.getLocalAddresses());
        if (value.getHost() == null || !value.getHost().matches("[A-Za-z0-9.-]{0,253}")
                || !value.isSetPort() || value.getPort() < 1 || value.getPort() > 65535) {
            throw new IllegalArgumentException("Use a DNS/IPv4 host or leave it empty for incoming-only links; port must be in 1..65535");
        }
        Link existing = config.getLink(new FTNAddr(address));
        String password = secret(value.getSessionPassword());
        if (password.isEmpty() && existing != null && existing.getPass() != null) {
            password = existing.getPass();
        }
        packetPassword(value, address, password, packets);
        String endpoint = value.getHost().isEmpty()
                ? ""
                : "," + value.getHost() + ":" + value.getPort();
        return address
               + ","
               + String.join(" ", local)
               + ","
               + (password.isEmpty() ? "-" : password)
               + endpoint;
    }

    private List<String> localAddresses(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 17) {
            throw new IllegalArgumentException("Configure between 1 and 17 local AKAs per link");
        }
        List<String> addresses = values.stream()
                                       .map(value -> AccountDirectory.address(value, false))
                                       .collect(Collectors.toList());
        if (new LinkedHashSet<>(addresses).size() != addresses.size()) {
            throw new IllegalArgumentException("Duplicate local AKA");
        }
        return addresses;
    }

    private void packetPassword(MailLinkConfiguration value,
                                String address,
                                String session,
                                Map<String, String> packets) {
        String entered = secret(value.getPacketPassword());
        String mode = value.getPacketPasswordMode();
        if ("UNPROTECTED".equals(mode)) {
            packets.put(address, "-");
            return;
        }
        if ("INHERIT".equals(mode)) {
            validatePacket(session);
            packets.remove(address);
            return;
        }
        if (!"SEPARATE".equals(mode)) {
            throw new IllegalArgumentException("Unknown PKT password mode");
        }
        String password = entered.isEmpty() ? packets.get(address) : entered;
        if (password == null || password.isEmpty() || password.equals("-")) {
            throw new IllegalArgumentException("Enter a separate PKT password");
        }
        validatePacket(password);
        packets.put(address, password);
    }

    private static String secret(String value) {
        if (value == null || value.length() > 4096 || !value.equals(value.trim())
                || value.chars().anyMatch(c -> c < 32 || c == 127 || c == ',' || c == '#')) {
            throw new IllegalArgumentException("Passwords cannot contain control characters, commas, # or surrounding spaces");
        }
        return value;
    }

    private static void validatePacket(String value) {
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(value) || value.length() > 8) {
            throw new IllegalArgumentException("PKT passwords support at most 8 ASCII characters; configure a separate PKT password for longer binkp passwords");
        }
    }

    private Map<String, String> packetPasswords(JFtnConfig config) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> entries = config.getValuesAsList("PacketPassword");
        if (entries == null) {
            return result;
        }
        for (String entry : entries) {
            String[] fields = entry.split(",", 2);
            if (fields.length != 2) {
                throw new IllegalArgumentException("Expected PacketPassword address,password");
            }
            result.put(AccountDirectory.address(fields[0].trim(), false), fields[1].trim());
        }
        return result;
    }

    TosserConfiguration tosser() throws Exception {
        JFtnConfig config = store.configuration();
        return new TosserConfiguration(Integer.parseInt(config.getValue("TossIntervalSeconds",
                                                                        "5")),
                                       config.isDeleteTossedFiles() != 0,
                                       Boolean.parseBoolean(config.getValue("EnforceEchoAccess",
                                                                            "true")),
                                       Boolean.parseBoolean(config.getValue("AutoCreateEcho",
                                                                            "false")));
    }

    void saveTosser(TosserConfiguration value) throws Exception {
        if (value == null || !value.isSetIntervalSeconds() || !value.isSetDeleteProcessedFiles()
                || !value.isSetEnforceEchoAccess() || !value.isSetAutoCreateEcho()
                || value.getIntervalSeconds() < 1 || value.getIntervalSeconds() > 86400) {
            throw new IllegalArgumentException("Tosser interval must be in 1..86400 seconds and all flags must be specified");
        }
        Map<String, List<String>> changes = new LinkedHashMap<>();
        changes.put("tossintervalseconds", List.of(Integer.toString(value.getIntervalSeconds())));
        changes.put("deletetossed", List.of(value.isDeleteProcessedFiles() ? "1" : "0"));
        changes.put("enforceechoaccess", List.of(Boolean.toString(value.isEnforceEchoAccess())));
        changes.put("autocreateecho", List.of(Boolean.toString(value.isAutoCreateEcho())));
        store.replaceOptions(changes);
    }
}
