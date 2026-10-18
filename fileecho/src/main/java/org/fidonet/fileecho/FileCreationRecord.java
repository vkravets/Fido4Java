package org.fidonet.fileecho;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "fileecho_creation")
public final class FileCreationRecord {
    @DatabaseField(id = true)
    String peer;
    @DatabaseField(canBeNull = false, width = 1024)
    String masks;
    @DatabaseField(canBeNull = false)
    String access;

    public FileCreationRecord() {
    }

    FileCreationRecord(String peer, String masks, String access) {
        this.peer = peer;
        this.masks = masks;
        this.access = access;
    }
}
