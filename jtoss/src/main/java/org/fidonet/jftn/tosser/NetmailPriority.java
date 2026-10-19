package org.fidonet.jftn.tosser;

import org.fidonet.types.Message;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Husky packMsg priority order; non-normal mail bypasses ordinary routing. */
enum NetmailPriority {
    NORMAL, CRASH, HOLD, DIRECT, IMMEDIATE;

    static NetmailPriority of(Message message) {
        Set<String> flags = Arrays.stream(NetmailRouter.configFlags(message).split("\\s+"))
                                  .collect(Collectors.toSet());
        boolean crash = (message.getAttributes() & 2) != 0 || flags.contains("CRA");
        boolean hold = (message.getAttributes() & 512) != 0 || flags.contains("HLD");
        if (flags.contains("IMM")) {
            return IMMEDIATE;
        }
        if (flags.contains("DIR") || crash && hold) {
            return DIRECT;
        }
        if (hold) {
            return HOLD;
        }
        return crash ? CRASH : NORMAL;
    }
}
