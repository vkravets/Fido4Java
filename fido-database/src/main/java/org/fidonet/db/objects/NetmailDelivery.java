package org.fidonet.db.objects;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "netmail_delivery")
public class NetmailDelivery {
    @DatabaseField(id = true, columnName = "delivery_key")
    private String key;

    @DatabaseField(foreign = true, foreignAutoRefresh = true, canBeNull = false)
    private Netmail message;

    @DatabaseField(canBeNull = false)
    private String state;

    @DatabaseField(columnName = "next_hop")
    private String nextHop;

    public NetmailDelivery() {
    }

    public NetmailDelivery(String key, Netmail message, String nextHop) {
        this.key = key;
        this.message = message;
        this.nextHop = nextHop;
        state = nextHop == null ? "LOCAL" : "QUEUED";
    }

    public String getKey() {
        return key;
    }

    public Netmail getMessage() {
        return message;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getNextHop() {
        return nextHop;
    }

    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }
}
