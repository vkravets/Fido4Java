package org.fidonet.db;

import org.fidonet.db.objects.Echoarea;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Area lookup and administration, independent of mail delivery. */
final class AreaRepository {
    private static final Logger logger = LoggerFactory.getLogger(AreaRepository.class);
    private final OrmManager dbManager;

    AreaRepository(OrmManager manager) {
        dbManager = manager;
    }
    public List<String> getAreas() {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        List<String> result = new ArrayList<String>();
        try {
            List<Echoarea> echoareaList = echoareasDao.queryBuilder()
                                                      .selectColumns(Echoarea.NAME_COLUMN)
                                                      .query();
            for (Echoarea echo : echoareaList) {
                result.add(echo.getName());
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return result;
    }

    public boolean isAreaExists(String name) {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        try {
            QueryBuilder<Echoarea, Long> echoareaQueryBuilder = echoareasDao.queryBuilder();
            echoareaQueryBuilder.setCountOf(true).where().eq(Echoarea.NAME_COLUMN, name);
            return echoareasDao.countOf(echoareaQueryBuilder.prepare()) == 1;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    public boolean createArea(String areaName, String description) {
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        Echoarea echoarea = new Echoarea();
        echoarea.setName(areaName);
        if (description != null) {
            echoarea.setDescription(description);
        }
        try {
            echoareas.create(echoarea);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    Echoarea findByName(String areaname) throws SQLException {
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        List<Echoarea> query = echoareaQueryBuilder.selectColumns(Echoarea.ID_COLUMN,
                                                                  Echoarea.NAME_COLUMN)
                                                   .where()
                                                   .eq(Echoarea.NAME_COLUMN, areaname)
                                                   .query();
        if (query.size() > 0) {
            return query.get(0);
        }
        return null;
    }
}
