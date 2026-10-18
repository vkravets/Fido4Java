package org.fidonet.fileecho;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "fileecho_area")
public final class FileAreaRecord {
    @DatabaseField(id = true)
    String name;
    @DatabaseField(canBeNull = false)
    String directory;
    @DatabaseField(canBeNull = false)
    String description;
    @DatabaseField
    boolean enabled;

    public FileAreaRecord() {
    }

    FileAreaRecord(String name, String directory, String description, boolean enabled) {
        this.name = name;
        this.directory = directory;
        this.description = description;
        this.enabled = enabled;
    }
}
