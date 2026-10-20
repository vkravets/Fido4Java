package org.fidonet.db.robots;

import org.fidonet.db.OrmManager;
import org.fidonet.types.FTNAddr;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Live rules from the mail database. This repository does not depend on the RPC account database. */
public final class DatabaseRobotRules implements EditableRobotRules {
    private final OrmManager orm;

    public DatabaseRobotRules(OrmManager orm) {
        this.orm = orm;
    }

    @Override
    public List<RobotAccessRule> list(RobotService service) {
        try {
            List<RobotAccessRule> result = new ArrayList<>();
            for (RobotRuleRecord row : orm.<RobotRuleRecord, String>getDao(RobotRuleRecord.class)
                                          .queryForEq("service", service.name())) {
                result.add(row.toRule());
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot read robot rules", failure);
        }
    }

    public void save(RobotAccessRule rule) {
        try {
            orm.getDao(RobotRuleRecord.class).createOrUpdate(new RobotRuleRecord(rule));
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot save robot rule", failure);
        }
    }

    @Override
    public RobotAccessRule find(RobotService service, FTNAddr peer) {
        try {
            RobotRuleRecord row = orm.<RobotRuleRecord, String>getDao(RobotRuleRecord.class)
                                     .queryForId(service.name() + ":" + peer);
            RobotAccessRule rule = row == null ? null : row.toRule();
            return rule != null && rule.isEnabled() ? rule : null;
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot read robot rule", failure);
        }
    }

    public void delete(RobotService service, FTNAddr peer) {
        try {
            orm.<RobotRuleRecord, String>getDao(RobotRuleRecord.class)
               .deleteById(service.name() + ":" + peer);
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot delete robot rule", failure);
        }
    }

    /** One-time explicit import into an empty rule store. Never replaces existing operator rules. */
    public void importIntoEmpty(List<RobotAccessRule> rules) throws SQLException {
        orm.inTransaction(() -> {
            if (orm.getDao(RobotRuleRecord.class).countOf() != 0) {
                throw new SQLException("Robot rule store is not empty; import refused");
            }
            for (RobotAccessRule rule : rules) {
                orm.getDao(RobotRuleRecord.class).create(new RobotRuleRecord(rule));
            }
            return null;
        });
    }
}
