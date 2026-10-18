package org.fidonet.binkp.common.codec;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.io.BinkFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/** Logs decoded protocol commands, never file blocks or authentication responses. */
public final class BinkpLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinkpLog.class);
    private static final int MAX_TEXT = 2048;

    private BinkpLog() {
    }

    public static void command(SessionContext context,
                               String connection,
                               boolean incoming,
                               BinkFrame frame) {
        if (frame.getDataInfo() >= 0 || frame.getData().length == 0) {
            return;
        }
        String line = "binkp "
                      + (incoming ? "<<<" : ">>>")
                      + " ["
                      + text(connection)
                      + "] "
                      + describe(frame);
        LOGGER.info(line);
        if (context != null) {
            context.recordProtocolLog(line);
        }
    }

    static String describe(BinkFrame frame) {
        byte[] data = frame.getData();
        BinkCommand command = BinkCommand.findCommand(data[0]);
        if (command == null) {
            return "UNKNOWN(" + Byte.toUnsignedInt(data[0]) + ") [payload omitted]";
        }
        if (command == BinkCommand.M_PWD) {
            return "PWD [redacted]";
        }
        String arguments = text(new String(data, 1, data.length - 1, StandardCharsets.UTF_8));
        String name = command.name().substring(2);
        return arguments.isEmpty() ? name : name + " " + arguments;
    }

    public static String text(String value) {
        StringBuilder safe = new StringBuilder();
        int length = Math.min(value.length(), MAX_TEXT);
        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || character == '\u2028'
                    || character == '\u2029') {
                safe.append(String.format("\\u%04x", (int) character));
            } else {
                safe.append(character);
            }
        }
        if (value.length() > length) {
            safe.append(" [truncated]");
        }
        return safe.toString();
    }
}
