package org.fidonet.db.robots;

import org.fidonet.types.FTNAddr;

import java.util.List;

/** Read interface shared by file and database rule providers. */
public interface RobotRules {
    List<RobotAccessRule> list(RobotService service);

    default RobotAccessRule find(RobotService service, FTNAddr peer) {
        for (RobotAccessRule rule : list(service)) {
            if (rule.getAddress().equals(peer.toString()) && rule.isEnabled()) {
                return rule;
            }
        }
        return null;
    }
}
