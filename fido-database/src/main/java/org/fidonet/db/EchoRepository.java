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

/** SQL cursors are independent of reader positions and transport acknowledgements. */
final class EchoRepository {
    private final OrmManager orm;

    EchoRepository(OrmManager orm) {
        this.orm = orm;
    }

    void visitNew(String area, String consumer, MessageIteration.Handler handler)
            throws IOException {
        String key = key(area, consumer);
        try {
            MessageCheckpoint checkpoint = orm.<MessageCheckpoint, String>getDao(MessageCheckpoint.class)
                                              .queryForId(key);
            QueryBuilder<Echomail, Object> query = query(area,
                                                         checkpoint == null
                                                                 ? 0
                                                                 : checkpoint.getLastId(),
                                                         Long.MAX_VALUE,
                                                         false);
            try (QueryCursor<Echomail> rows = new QueryCursor<>(query)) {
                visitRows(rows, key, handler);
            }
        } catch (SQLException e) {
            throw new IOException("Unable to scan new echo messages", e);
        }
    }

    void initializeConsumer(String area, String consumer, boolean onlyNew) throws SQLException {
        String key = key(area, consumer);
        if (orm.<MessageCheckpoint, String>getDao(MessageCheckpoint.class)
               .queryForId(key) != null) {
            return;
        }
        Echomail latest = onlyNew
                ? query(area, 0, Long.MAX_VALUE, true).limit(1L).queryForFirst()
                : null;
        orm.getDao(MessageCheckpoint.class)
           .create(new MessageCheckpoint(key, latest == null ? 0 : latest.getId()));
    }

    void visitRange(String area, long firstId, long lastId, MessageIteration.Handler handler)
            throws IOException {
        if (firstId < 0 || lastId < firstId) {
            throw new IllegalArgumentException("Invalid echo message ID range");
        }
        try (QueryCursor<Echomail> rows = new QueryCursor<>(query(area,
                                                                  firstId - 1,
                                                                  lastId,
                                                                  false))) {
            visitRangeRows(rows, handler);
        } catch (SQLException e) {
            throw new IOException("Unable to read echo history", e);
        }
    }

    void visitRecent(String area, int count, MessageIteration.Handler handler) throws IOException {
        if (count < 1 || count > 10000) {
            throw new IllegalArgumentException("Recent echo count must be 1..10000");
        }
        try (QueryCursor<Echomail> rows = new QueryCursor<>(query(area,
                                                                  0,
                                                                  Long.MAX_VALUE,
                                                                  true).limit((long) count))) {
            visitRangeRows(rows, handler);
        } catch (SQLException e) {
            throw new IOException("Unable to read recent echo history", e);
        }
    }

    private void visitRangeRows(QueryCursor<Echomail> rows, MessageIteration.Handler handler)
            throws IOException {
        while (rows.hasNext()) {
            handler.accept(rows.next().toMessage());
        }
    }

    private QueryBuilder<Echomail, Object> query(String name,
                                                 long after,
                                                 long through,
                                                 boolean descending)
            throws SQLException {
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
        query.orderBy(Echomail.ID_COLUMN, !descending)
             .where()
             .eq(Echomail.ID_AREA_COLUMN, area.getId())
             .and()
             .gt(Echomail.ID_COLUMN, after)
             .and()
             .le(Echomail.ID_COLUMN, through);
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

    private String key(String area, String consumer) {
        return UUID.nameUUIDFromBytes((consumer + "\0"
                + area.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
