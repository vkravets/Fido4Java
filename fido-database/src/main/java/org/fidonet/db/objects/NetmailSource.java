package org.fidonet.db.objects;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/** Authenticated transport origin, independent of the selected outgoing route. */
@DatabaseTable(tableName = "netmail_source")
public class NetmailSource {
    @DatabaseField(id = true, columnName = "delivery_key")
    private String key;

    @DatabaseField(canBeNull = false)
    private String peer;

    public NetmailSource() {
    }

    public NetmailSource(String key, String peer) {
        this.key = key;
        this.peer = peer;
    }

    public String getPeer() {
        return peer;
    }
}
