package org.fidonet.db.objects;

import org.fidonet.packet.MailIdentity;
import org.fidonet.types.Message;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/** Search keys kept separately so legacy message tables need no destructive migration. */
@DatabaseTable(tableName = "netmail_index")
public class NetmailIndex {
    @DatabaseField(id = true, columnName = "message_id")
    private Long messageId;
    @DatabaseField(index = true, canBeNull = false)
    private String identity;
    @DatabaseField(index = true, columnName = "from_addr", canBeNull = false)
    private String from;
    @DatabaseField(index = true, columnName = "to_addr", canBeNull = false)
    private String to;

    public NetmailIndex() {
    }

    public NetmailIndex(Netmail stored) {
        Message message = stored.toMessage();
        messageId = stored.getId();
        identity = MailIdentity.of(message);
        from = message.getFAddr().toString();
        to = message.getTAddr().toString();
    }
}
