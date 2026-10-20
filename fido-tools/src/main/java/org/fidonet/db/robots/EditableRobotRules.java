package org.fidonet.db.robots;

import org.fidonet.types.FTNAddr;

import java.util.List;

/** Administrative changes to robot access, without exposing the storage implementation. */
public interface EditableRobotRules extends RobotRules {
    void save(RobotAccessRule rule);

    void delete(RobotService service, FTNAddr peer);

    void importIntoEmpty(List<RobotAccessRule> rules) throws Exception;
}
