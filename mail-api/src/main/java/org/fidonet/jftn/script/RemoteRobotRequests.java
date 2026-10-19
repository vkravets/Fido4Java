package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/** Trusted local automation that queues ordinary netmail to an uplink's robot. */
public final class RemoteRobotRequests {
    private final JFtnConfig config;
    private final Tosser tosser;
    private final ScriptState state;

    public RemoteRobotRequests(JFtnConfig config, Tosser tosser, ScriptState state) {
        this.config = config;
        this.tosser = tosser;
        this.state = state;
    }

    public String submit(String robot,
                         FTNAddr destination,
                         FTNAddr author,
                         String password,
                         List<String> commands,
                         String requestId)
            throws IOException {
        String service = service(robot);
        Link link = config.getLink(destination);
        if (link == null || config.getLocalAddresses().contains(destination)
                || link.getMyaddr() == null || !link.getMyaddr().contains(author)) {
            throw new IllegalArgumentException("Robot target must be a remote link with an allowed local AKA");
        }
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("A stable robot request ID is required");
        }
        if (password == null || password.isBlank()
                || password.chars().anyMatch(Character::isISOControl)
                || password.getBytes(StandardCharsets.UTF_8).length > 71) {
            throw new IllegalArgumentException("Robot password must fit the netmail subject");
        }
        String body = body(commands);
        String key = "remote-robot:"
                + ScriptState.digest(service + ":" + destination + ":" + author + ":" + requestId);
        String fingerprint = ScriptState.digest(password + "\0" + body);
        Message message = new Message(config.getValue("SysOp", "Sysop"),
                                      service,
                                      author,
                                      destination,
                                      password,
                                      body,
                                      new Date());
        message.setAttributes(1);
        message.getBody().getKludges().put("CHRS", "UTF-8 4");
        return queue(message, key, fingerprint);
    }

    private String queue(Message message, String key, String fingerprint) throws IOException {
        synchronized (RemoteRobotRequests.class) {
            String previous = state.get(key);
            if (previous != null && !previous.equals(fingerprint)) {
                throw new IllegalArgumentException("Robot request ID was already used for different content");
            }
            state.put(key, fingerprint);
            tosser.submitNetmail(message, key);
            return key;
        }
    }

    private static String service(String value) {
        if ("AreaFix".equalsIgnoreCase(value)) {
            return "AreaFix";
        }
        if ("FileFix".equalsIgnoreCase(value)) {
            return "FileFix";
        }
        throw new IllegalArgumentException("Robot must be AreaFix or FileFix");
    }

    private static String body(List<String> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > 200) {
            throw new IllegalArgumentException("Supply 1..200 robot command lines");
        }
        StringBuilder result = new StringBuilder();
        for (String command : commands) {
            if (command == null || command.isBlank()
                    || command.chars().anyMatch(Character::isISOControl)
                    || command.trim().startsWith("---")) {
                throw new IllegalArgumentException("Each robot command must be one nonempty text line");
            }
            result.append(command.trim()).append('\r');
            if (result.length() > 64000) {
                throw new IllegalArgumentException("Robot request exceeds 64000 characters");
            }
        }
        return result.toString();
    }
}
