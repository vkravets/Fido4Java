package org.fidonet.db.robots;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "mail_robot_access")
public final class RobotRuleRecord {
    @DatabaseField(id = true)
    private String id;
    @DatabaseField(canBeNull = false)
    private String service;
    @DatabaseField(canBeNull = false)
    private String address;
    @DatabaseField(canBeNull = false, width = 512)
    private String credential;
    @DatabaseField(canBeNull = false, width = 4096)
    private String masks;
    @DatabaseField(canBeNull = false)
    private String access;
    @DatabaseField(canBeNull = false)
    private boolean enabled;

    public RobotRuleRecord() {
    }

    RobotRuleRecord(RobotAccessRule rule) {
        service = rule.getService().name();
        address = rule.getAddress();
        id = service + ":" + address;
        credential = rule.credential();
        masks = rule.getMasks();
        access = rule.getAccess();
        enabled = rule.isEnabled();
    }

    RobotAccessRule toRule() {
        return new RobotAccessRule(RobotService.valueOf(service),
                                   address,
                                   credential,
                                   masks,
                                   access,
                                   enabled);
    }
}
