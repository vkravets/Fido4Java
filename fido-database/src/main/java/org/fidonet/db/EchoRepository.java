package org.fidonet.db;

import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.db.objects.MessageCheckpoint;
import org.fidonet.echobase.MessageIteration;

import com.j256.ormlite.stmt.QueryBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

/** SQL cursors are independent of reader last-read positions and transport acknowledgements. */
final class EchoRepository {
    private final OrmManager orm;

    EchoRepository(OrmManager orm) {
        this.orm = orm;
    }

    void visitNew(String area, String consumer, MessageIteration.Handler handler)
            throws IOException {
        String key = UUID.nameUUIDFromBytes((consumer + "\0"
                + area.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)).toString();
        try (QueryCursor<Echomail> rows = new QueryCursor<>(query(area, key))) {
            visitRows(rows, key, handler);
        } catch (SQLException e) {
            throw new IOException("Unable to scan new echo messages", e);
        }
    }

    private QueryBuilder<Echomail, Object> query(String name, String key) throws SQLException {
        MessageCheckpoint checkpoint = orm.<MessageCheckpoint, String>getDao(MessageCheckpoint.class)
                                          .queryForId(key);
        Echoarea area = orm.<Echoarea, Object>getDao(Echoarea.class)
                           .queryBuilder()
                           .where()
                           .eq(Echoarea.NAME_COLUMN, name.toLowerCase(Locale.ROOT))
                           .queryForFirst();
        if (area == null) {
            throw new SQLException("Unknown echo area " + name);
        }
        QueryBuilder<Echomail, Object> query = orm.<Echomail, Object>getDao(Echomail.class)
                                                  .queryBuilder();
        query.orderBy(Echomail.ID_COLUMN, true)
             .where()
             .eq(Echomail.ID_AREA_COLUMN, area.getId())
             .and()
             .gt(Echomail.ID_COLUMN, checkpoint == null ? 0 : checkpoint.getLastId());
        return query;
    }

    private void visitRows(QueryCursor<Echomail> rows, String key, MessageIteration.Handler handler)
            throws IOException, SQLException {
        while (rows.hasNext()) {
            Echomail row = rows.next();
            handler.accept(row.toMessage());
            orm.getDao(MessageCheckpoint.class)
               .createOrUpdate(new MessageCheckpoint(key, row.getId()));
        }
    }
}
