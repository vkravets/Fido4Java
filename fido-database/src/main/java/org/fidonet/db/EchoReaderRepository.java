package org.fidonet.db;

import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.echobase.StoredMail;

import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Area-scoped indexed parent lookup and bounded SQL reply pages, including existing mail. */
final class EchoReaderRepository {
    private final OrmManager orm;

    EchoReaderRepository(OrmManager orm) {
        this.orm = orm;
    }

    StoredMail byMessageId(String name, String messageId) throws IOException {
        try {
            Echoarea area = area(name);
            QueryBuilder<Echomail, Object> query = orm.<Echomail, Object>getDao(Echomail.class)
                                                      .queryBuilder();
            query.orderBy(Echomail.ID_COLUMN, true)
                 .limit(1L)
                 .where()
                 .eq(Echomail.ID_AREA_COLUMN, area.getId())
                 .and()
                 .eq(Echomail.MSGID_COLUMN, messageId);
            Echomail row = query.queryForFirst();
            return row == null ? null : stored(row, area);
        } catch (SQLException failure) {
            throw new IOException("Unable to find echo parent", failure);
        }
    }

    List<StoredMail> recent(String name, long before, int limit) throws IOException {
        try {
            Echoarea area = area(name);
            QueryBuilder<Echomail, Object> query = orm.<Echomail, Object>getDao(Echomail.class)
                                                      .queryBuilder();
            query.orderBy(Echomail.ID_COLUMN, false)
                 .limit((long) limit)
                 .where()
                 .eq(Echomail.ID_AREA_COLUMN, area.getId())
                 .and()
                 .lt(Echomail.ID_COLUMN, before == 0 ? Long.MAX_VALUE : before);
            List<StoredMail> result = new ArrayList<>();
            for (Echomail row : query.query())
                result.add(stored(row, area));
            return result;
        } catch (SQLException failure) {
            throw new IOException("Unable to read recent echo mail", failure);
        }
    }

    List<StoredMail> replies(String name, String messageId, long after, int limit, long excluded)
            throws IOException {
        if (messageId.isEmpty())
            return List.of();
        String line = "\1REPLY: " + messageId + "\r";
        String pattern = "%" + line.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        try {
            Echoarea area = area(name);
            QueryBuilder<Echomail, Object> query = orm.<Echomail, Object>getDao(Echomail.class)
                                                      .queryBuilder();
            query.orderBy(Echomail.ID_COLUMN, true)
                 .limit((long) limit)
                 .where()
                 .eq(Echomail.ID_AREA_COLUMN, area.getId())
                 .and()
                 .gt(Echomail.ID_COLUMN, after)
                 .and()
                 .ne(Echomail.ID_COLUMN, excluded)
                 .and()
                 .raw(Echomail.TEXT_COLUMN + " LIKE ? ESCAPE '!'",
                      new SelectArg(SqlType.STRING, pattern));
            List<StoredMail> result = new ArrayList<>();
            for (Echomail row : query.query())
                result.add(stored(row, area));
            return result;
        } catch (SQLException failure) {
            throw new IOException("Unable to read echo replies", failure);
        }
    }

    private Echoarea area(String name) throws SQLException {
        Echoarea area = orm.<Echoarea, Object>getDao(Echoarea.class)
                           .queryBuilder()
                           .where()
                           .eq(Echoarea.NAME_COLUMN, name.toLowerCase(Locale.ROOT))
                           .queryForFirst();
        if (area == null)
            throw new IllegalArgumentException("Unknown echo area");
        return area;
    }

    private StoredMail stored(Echomail row, Echoarea area) {
        row.setArea(area);
        return new StoredMail(row.getId(), row.toMessage());
    }
}
