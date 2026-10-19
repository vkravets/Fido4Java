package org.fidonet.jftn.poll;

import org.fidonet.binkp.common.ClientConnector;
import org.fidonet.binkp.common.protocol.ProcessConnector;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** File-managed SSH mappings; the normal link retains its AKA and binkp credentials. */
public final class OutboundTransportFactory {
    private final JFtnConfig config;

    public OutboundTransportFactory(JFtnConfig config) {
        this.config = config;
    }

    public void validate() {
        routes();
    }

    public ClientConnector create(Link link) {
        String[] route = routes().get(link.getAddr());
        if (route == null) {
            return new Client(link);
        }
        return new ProcessConnector(link,
                                    command(route),
                                    Duration.ofSeconds(30),
                                    "SSH " + route[1] + " peer=" + link.getAddr());
    }

    List<String> command(String[] route) {
        List<String> command = new ArrayList<>();
        command.add(config.getValue("BinkpSshExecutable", "ssh"));
        command.addAll(List.of("-T", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes"));
        String settings = config.getValue("BinkpSshConfig", "");
        if (!settings.isBlank()) {
            command.add("-F");
            command.add(localPath(settings).toString());
        }
        command.add(route[1]);
        String executable = route.length == 4 ? route[3] : "binkd";
        command.add("exec " + quote(executable) + " -i " + quote(route[2]));
        return List.copyOf(command);
    }

    private Map<FTNAddr, String[]> routes() {
        Map<FTNAddr, String[]> routes = new LinkedHashMap<>();
        List<String> configured = config.getValuesAsList("BinkpSsh");
        if (configured == null) {
            return routes;
        }
        for (String value : configured) {
            String[] route = value.split("\\|", -1);
            if (route.length < 3 || route.length > 4) {
                throw new IllegalArgumentException("BinkpSsh requires peer|SSH-alias|remote-config[|binkd]");
            }
            for (int index = 0; index < route.length; index++) {
                route[index] = route[index].trim();
                if (route[index].isEmpty()
                        || route[index].chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Empty or invalid BinkpSsh field");
                }
            }
            FTNAddr address = new FTNAddr(route[0]);
            if (!address.isValid() || config.getLink(address) == null) {
                throw new IllegalArgumentException("BinkpSsh peer must be a configured link");
            }
            if (!route[1].matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("BinkpSsh requires an SSH host alias");
            }
            if (route[2].startsWith("-")) {
                throw new IllegalArgumentException("Remote binkd configuration cannot be an option");
            }
            if (routes.putIfAbsent(address, route) != null) {
                throw new IllegalArgumentException("Duplicate BinkpSsh peer");
            }
        }
        String settings = config.getValue("BinkpSshConfig", "");
        String executable = config.getValue("BinkpSshExecutable", "ssh");
        if (executable.isBlank() || executable.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid BinkpSshExecutable");
        }
        if (!routes.isEmpty() && !settings.isBlank() && (!Files.isRegularFile(localPath(settings))
                || !Files.isReadable(localPath(settings)))) {
            throw new IllegalArgumentException("BinkpSshConfig is not readable");
        }
        return routes;
    }

    private Path localPath(String value) {
        Path source = config.getSourcePath();
        Path root = source == null ? Path.of("").toAbsolutePath() : source.getParent();
        return root.resolve(value).normalize();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
