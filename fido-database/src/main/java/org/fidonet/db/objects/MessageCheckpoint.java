package org.fidonet.db.objects;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "message_checkpoint")
public class MessageCheckpoint {
    @DatabaseField(id = true)
    private String consumer;
    @DatabaseField(canBeNull = false)
    private long lastId;

    public MessageCheckpoint() {
    }

    public MessageCheckpoint(String consumer, long lastId) {
        this.consumer = consumer;
        this.lastId = lastId;
    }

    public long getLastId() {
        return lastId;
    }
}
