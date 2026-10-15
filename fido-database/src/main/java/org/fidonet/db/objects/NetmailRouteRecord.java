package org.fidonet.db.objects;

import org.fidonet.echobase.NetmailRoute;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "netmail_route")
public class NetmailRouteRecord {
    @DatabaseField(id = true)
    private String id;
    @DatabaseField(canBeNull = false)
    private int priority;
    @DatabaseField(canBeNull = false)
    private String pattern;
    @DatabaseField(canBeNull = false, columnName = "next_hop")
    private String nextHop;
    @DatabaseField(canBeNull = false)
    private boolean enabled;

    public NetmailRouteRecord() {
    }

    public NetmailRouteRecord(NetmailRoute route) {
        id = route.getId();
        priority = route.getPriority();
        pattern = route.getPattern();
        nextHop = route.getNextHop();
        enabled = route.isEnabled();
    }

    public NetmailRoute toRoute() {
        return new NetmailRoute(id, priority, pattern, nextHop, enabled);
    }
}
