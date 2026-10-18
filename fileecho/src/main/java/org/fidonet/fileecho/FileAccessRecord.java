package org.fidonet.fileecho;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "fileecho_access")
public final class FileAccessRecord {
    @DatabaseField(id = true)
    String id;
    @DatabaseField(canBeNull = false)
    String area;
    @DatabaseField(canBeNull = false)
    String peer;
    @DatabaseField(canBeNull = false)
    String access;

    public FileAccessRecord() {
    }

    FileAccessRecord(String area, String peer, String access) {
        id = area + ":" + peer;
        this.area = area;
        this.peer = peer;
        this.access = access;
    }
}
