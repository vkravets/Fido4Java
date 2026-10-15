package org.fidonet.db;

import org.fidonet.db.objects.Netmail;
import org.fidonet.db.objects.NetmailDelivery;
import org.fidonet.db.objects.NetmailIndex;
import org.fidonet.echobase.MessageIteration;
import org.fidonet.echobase.NetmailStore;
import org.fidonet.echobase.QueuedNetmail;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import com.j256.ormlite.stmt.QueryBuilder;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

/** Indexed, streaming netmail reads and search-key maintenance for new messages. */
final class NetmailRepository {
    private final OrmManager orm;

    NetmailRepository(OrmManager orm) {
        this.orm = orm;
    }

    void index(Netmail message) throws SQLException {
        orm.getDao(NetmailIndex.class).createOrUpdate(new NetmailIndex(message));
    }

    boolean contains(Message message) throws SQLException {
        return orm.getDao(NetmailIndex.class)
                  .queryBuilder()
                  .where()
                  .eq("identity", MailIdentity.of(message))
                  .countOf() != 0;
    }

    void visitEditor(FTNAddr address, MessageIteration.Handler visitor) throws IOException {
        try (QueryCursor<Netmail> rows = new QueryCursor<>(editorQuery(address))) {
            visitEditorRows(rows, visitor);
        } catch (SQLException e) {
            throw new IOException("Unable to read editor netmail", e);
        }
    }

    private QueryBuilder<Netmail, Object> editorQuery(FTNAddr address) throws SQLException {
        QueryBuilder<NetmailIndex, Object> keys = orm.<NetmailIndex, Object>getDao(NetmailIndex.class)
                                                     .queryBuilder();
        keys.selectColumns("message_id")
            .where()
            .eq("from_addr", address.toString())
            .or()
            .eq("to_addr", address.toString());
        QueryBuilder<Netmail, Object> query = orm.<Netmail, Object>getDao(Netmail.class)
                                                 .queryBuilder();
        query.where().in("id", keys);
        return query;
    }

    private void visitEditorRows(Iterator<Netmail> rows, MessageIteration.Handler visitor)
            throws IOException {
        while (rows.hasNext()) {
            Netmail stored = rows.next();
            Message message = stored.toMessage();
            if (Boolean.TRUE.equals(stored.getSend())) {
                message.setAttributes(message.getAttributes() | 8);
            }
            visitor.accept(message);
        }
    }

    void visitQueued(NetmailStore.QueuedHandler visitor) throws IOException {
        try (QueryCursor<NetmailDelivery> rows = new QueryCursor<>(queuedQuery())) {
            visitQueuedRows(rows, visitor);
        } catch (SQLException e) {
            throw new IOException("Unable to read outgoing netmail", e);
        }
    }

    private QueryBuilder<NetmailDelivery, Object> queuedQuery() throws SQLException {
        QueryBuilder<NetmailDelivery, Object> query = orm.<NetmailDelivery, Object>getDao(NetmailDelivery.class)
                                                         .queryBuilder();
        query.where().eq("state", "QUEUED");
        return query;
    }

    private void visitQueuedRows(Iterator<NetmailDelivery> rows, NetmailStore.QueuedHandler visitor)
            throws IOException {
        while (rows.hasNext()) {
            NetmailDelivery row = rows.next();
            visitor.accept(new QueuedNetmail(row.getKey(),
                                             row.getNextHop(),
                                             row.getMessage().toMessage()));
        }
    }
}
