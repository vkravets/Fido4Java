package org.fidonet.db.objects;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

/** Provenance and durable prohibition on automatic redistribution of archives. */
@DatabaseTable(tableName = "archive_receipt")
public class ArchiveReceipt {
    @DatabaseField(id = true)
    private String identity;
    @DatabaseField(dataType = DataType.LONG_STRING, canBeNull = false)
    private String source;
    @DatabaseField(canBeNull = false)
    private Date imported;

    public ArchiveReceipt() {
    }

    public ArchiveReceipt(String identity, String source) {
        this.identity = identity;
        this.source = source;
        imported = new Date();
    }

    public String getSource() {
        return source;
    }
}
