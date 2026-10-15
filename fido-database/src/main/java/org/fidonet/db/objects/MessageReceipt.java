package org.fidonet.db.objects;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/** Duplicate history survives removal of message contents. */
@DatabaseTable(tableName = "message_receipt")
public class MessageReceipt {
    @DatabaseField(id = true)
    private String identity;

    public MessageReceipt() {
    }

    public MessageReceipt(String identity) {
        this.identity = identity;
    }
}
