package org.fidonet.jftn.script;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Common request identity, credential checks and durable replies; commands belong to scripts. */
public final class RobotRequest {
    private final MailScripts api;
    private final Message message;
    private final String robot;
    private final String id;
    private final List<String> policy;

    private RobotRequest(MailScripts api,
                         Message message,
                         String robot,
                         String deliveryKey,
                         List<String> policy) {
        this.api = api;
        this.message = message;
        this.robot = robot;
        this.policy = List.copyOf(policy);
        id = ScriptState.digest(robot
                                + ":"
                                + message.getFAddr()
                                + ":"
                                + (message.getMsgId() == null ? deliveryKey : message.getMsgId()));
    }

    /** Only direct requests from an explicitly configured robot peer are authorized. */
    public static RobotRequest authorize(MailScripts api,
                                         String robot,
                                         Message message,
                                         Link peer,
                                         String deliveryKey) {
        if (!message.getFAddr().equals(peer.getAddr()) || message.isEchomail()
                || (message.getAttributes() & 0x8810) != 0) {
            return null;
        }
        List<String> values = api.getConfig().getValuesAsList(robot + "Peer");
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String[] fields = value.split("\\|", -1);
            if (fields.length < 3) {
                throw new IllegalArgumentException("Expected "
                                                   + robot
                                                   + "Peer=address|password|areas");
            }
            if (!new FTNAddr(fields[0].trim()).equals(peer.getAddr())) {
                continue;
            }
            if (fields[1].isEmpty()
                    || !MessageDigest.isEqual(fields[1].getBytes(StandardCharsets.UTF_8),
                                              message.getSubject()
                                                     .getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            List<String> policy = new ArrayList<>();
            for (int i = 2; i < fields.length; i++) {
                policy.add(fields[i].trim());
            }
            return new RobotRequest(api, message, robot, deliveryKey, policy);
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public List<String> getPolicy() {
        return policy;
    }

    public boolean allows(String area) {
        for (String mask : policy.get(0).split(",")) {
            if (matches(mask.trim(), area)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String mask, String value) {
        StringBuilder expression = new StringBuilder("^");
        for (char character : mask.toLowerCase(Locale.ROOT).toCharArray()) {
            expression.append(character == '*'
                    ? ".*"
                    : character == '?' ? "." : Pattern.quote(String.valueOf(character)));
        }
        return value.toLowerCase(Locale.ROOT).matches(expression + "$");
    }

    public String previousReply() throws IOException {
        return api.getState().get("reply:" + id);
    }

    public void reply(String text) throws IOException {
        if (text.length() > 48000) {
            text = text.substring(0, 47900) + "\rResponse truncated; use a narrower area mask.\r";
        }
        api.getState().put("reply:" + id, text);
        Message reply = new Message(robot,
                                    message.getFrom(),
                                    message.getTAddr(),
                                    message.getFAddr(),
                                    robot + " response",
                                    text,
                                    new Date());
        reply.setAttributes(1);
        reply.setMsgId(message.getTAddr() + " " + id);
        if (message.getMsgId() != null) {
            reply.getBody().getKludges().put("REPLY", message.getMsgId());
        }
        api.getTosser().submitNetmail(reply, "robot:" + id);
    }
}
