package org.fidonet.db.objects;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 9:32 AM
 */
@DatabaseTable(tableName = "echoarea")
public class Echoarea {

    @DatabaseField(columnName = "id", generatedId = true, canBeNull = false, unique = true, uniqueIndex = true)
    private Long id;

    @DatabaseField(columnName = "name", canBeNull = false, uniqueIndex = true)
    private String name;

    @DatabaseField(columnName = "description", canBeNull = true)
    private String description;

    public Echoarea() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
