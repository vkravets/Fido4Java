package org.fidonet.jftn.script;

import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Common request identity, credential checks and durable replies; commands belong to scripts. */
public final class RobotRequest {
    private final MailAutomationRuntime api;
    private final Message message;
    private final String robot;
    private final String id;
    private final List<String> policy;

    private RobotRequest(MailAutomationRuntime api,
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
    public static RobotRequest authorize(MailAutomationRuntime api,
                                         String robot,
                                         Message message,
                                         Link peer,
                                         String deliveryKey) {
        if (!message.getFAddr().equals(peer.getAddr()) || message.isEchomail()
                || (message.getAttributes() & 0x8810) != 0) {
            return null;
        }
        RobotAccessRule rule = api.getRobotAccess()
                                  .authorize(robot, peer.getAddr(), message.getSubject());
        if (rule == null) {
            return null;
        }
        List<String> policy = robot.equals("AreaFix")
                ? List.of(rule.getMasks(), rule.getAccess())
                : List.of(rule.getMasks());
        return new RobotRequest(api, message, robot, deliveryKey, policy);
    }

    public String getId() {
        return id;
    }

    /** Parse transport line endings and stop at the tear line before executing any commands. */
    public static List<String> commands(String body) {
        if (body.length() > 64000) {
            throw new IllegalArgumentException("Request exceeds 200 lines or 64000 characters.");
        }
        String[] lines = body.split("\\r\\n|\\r|\\n", -1);
        if (lines.length > 201) {
            throw new IllegalArgumentException("Request exceeds 200 lines or 64000 characters.");
        }
        List<String> result = new ArrayList<>();
        for (String value : lines) {
            String line = value.trim();
            if (line.equals("---") || line.startsWith("--- ")) {
                break;
            }
            if (!line.isEmpty() && !value.stripLeading().startsWith("\u0001")) {
                result.add(line);
            }
        }
        return List.copyOf(result);
    }

    /** A query accepts one mask, optionally preceded by a separate exclusion marker. */
    public static boolean selects(String[] fields, String area) {
        if (fields.length == 1) {
            return true;
        }
        if (fields.length == 2 && !fields[1].equals("!")) {
            return matches(fields[1], area);
        }
        if (fields.length == 3 && fields[1].equals("!")) {
            return !matches(fields[2], area);
        }
        throw new IllegalArgumentException("Usage: command [! mask | mask]");
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
        return RobotAccessRule.matches(mask, value);
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
