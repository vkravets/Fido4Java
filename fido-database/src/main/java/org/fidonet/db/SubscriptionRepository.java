package org.fidonet.db;

import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Subscription;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.types.Link;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Subscription permissions and reader positions, separate from export checkpoints. */
final class SubscriptionRepository {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRepository.class);
    private final OrmManager dbManager;
    private final AreaRepository areas;

    SubscriptionRepository(OrmManager manager, AreaRepository areas) {
        dbManager = manager;
        this.areas = areas;
    }
    public List<String> getSubscriptions(Link link) {
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        Dao<Echoarea, Object> echoareasDao = dbManager.getDao(Echoarea.class);

        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder()
                                                                            .selectColumns(ConfigurationLink.ID_COLUMN,
                                                                                           ConfigurationLink.ADDRESS_COLUMN);
        QueryBuilder<Echoarea, Object> echoareasQueryBuilder = echoareasDao.queryBuilder()
                                                                           .selectColumns(Echoarea.ID_COLUMN,
                                                                                          Echoarea.NAME_COLUMN);

        try {
            linksQueryBuilder.selectColumns(ConfigurationLink.ID_COLUMN)
                             .where()
                             .eq(ConfigurationLink.ADDRESS_COLUMN, link.getAddr().as5D());

            QueryBuilder<Subscription, Object> joinLinks = subscriptionsDao.queryBuilder()
                                                                           .join(linksQueryBuilder);
            QueryBuilder<Echoarea, Object> joinArea = echoareasQueryBuilder.join(joinLinks);

            List<Echoarea> subscriptionList = joinArea.query();
            List<String> arrayList = new ArrayList<String>();
            for (Echoarea subscription : subscriptionList) {
                arrayList.add(subscription.getName());
            }

            return arrayList;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public void addSubscription(Link link, String area) {
        List<String> subscriptions = getSubscriptions(link);
        if (subscriptions.contains(area.toLowerCase(Locale.ROOT))) {
            return;
        }

        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        try {
            ConfigurationLink configurationLink = findLink(link);
            if (configurationLink == null) {
                logger.warn("Link {} is not found, skip adding subscription",
                            link.getAddr().as5D());
                return;
            }

            Echoarea echoarea = areas.findByName(area);
            if (echoarea == null) {
                logger.warn("Trying adding subscription to {} link for non-existing are ({})",
                            link.getAddr().as5D(),
                            area);
                return;
            }

            Subscription subscription = new Subscription();
            subscription.setConfigurationLink(configurationLink);
            subscription.setEchoarea(echoarea);
            subscription.setLastMessage(0L);
            subscription.setAccessLevel(AccessLevel.BOTH);
            subscriptionsDao.create(subscription);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void setLinkLastMessage(Link link, String areaname, Long id) {
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        QueryBuilder<Subscription, Object> subscriptionsQueryBuilder = subscriptionsDao.queryBuilder();

        try {
            ConfigurationLink configurationLink = findLink(link);
            if (configurationLink == null) {
                logger.warn("Link {} is not found, skip adding subscription",
                            link.getAddr().as5D());
                return;
            }

            Echoarea echoarea = areas.findByName(areaname);
            if (echoarea == null) {
                logger.warn("Trying to change last message id for non-existing area ({}) for {} link",
                            areaname,
                            link.getAddr().as5D());
                return;
            }

            Where<Subscription, Object> subscriptionObjectWhere = subscriptionsQueryBuilder.where()
                                                                                           .eq(Subscription.ID_AREA_COLUMN,
                                                                                               echoarea.getId())
                                                                                           .and()
                                                                                           .eq(Subscription.ID_LINK_COLUMN,
                                                                                               configurationLink.getId());
            List<Subscription> subscriptionList = subscriptionObjectWhere.query();
            if (subscriptionList.size() > 0) {
                UpdateBuilder<Subscription, Object> subscriptionUpdateBuilder = subscriptionsDao.updateBuilder();
                subscriptionUpdateBuilder.updateColumnValue(Subscription.LASTMESSAGE_ID_COLUMN, id);
                subscriptionUpdateBuilder.setWhere(subscriptionObjectWhere);
                subscriptionsDao.update(subscriptionUpdateBuilder.prepare());
            } else {
                logger.warn("{} link don't subscribe to {} area", link.getAddr().as5D(), areaname);
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    Map<String, AccessLevel> accessForLink(Link link) {
        try {
            ConfigurationLink configured = findLink(link);
            Map<String, AccessLevel> result = new TreeMap<>();
            if (configured == null) {
                return result;
            }
            for (Subscription subscription : forLink(configured)) {
                result.put(subscription.getEchoarea().getName(), subscription.getAccessLevel());
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to list echo access", e);
        }
    }

    boolean hasAccess(Link link, String area, AccessLevel requested) {
        try {
            ConfigurationLink configured = findLink(link);
            if (configured == null) {
                return false;
            }
            for (Subscription subscription : forLink(configured)) {
                if (subscription.getEchoarea().getName().equalsIgnoreCase(area)) {
                    return subscription.getAccessLevel() == AccessLevel.BOTH
                            || subscription.getAccessLevel() == requested;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to check echo access", e);
        }
    }

    List<Subscription> forLink(ConfigurationLink confLink) throws SQLException {
        Dao<Subscription, Object> dao = dbManager.getDao(Subscription.class);
        QueryBuilder<Subscription, Object> queryBuilder = dao.queryBuilder();
        //                selectColumns(Subscription.ID_AREA_COLUMN,
        // Subscription.ID_LINK_COLUMN, Subscription.LASTMESSAGE_ID_COLUMN).
        return queryBuilder.where().eq(Subscription.ID_LINK_COLUMN, confLink.getId()).query();
    }

    ConfigurationLink findLink(Link link) throws SQLException {
        Dao<ConfigurationLink, Object> dao = dbManager.getDao(ConfigurationLink.class);
        QueryBuilder<ConfigurationLink, Object> queryBuilder = dao.queryBuilder();
        List<ConfigurationLink> query = queryBuilder.selectColumns(ConfigurationLink.ID_COLUMN,
                                                                   ConfigurationLink.ADDRESS_COLUMN)
                                                    .where()
                                                    .eq(ConfigurationLink.ADDRESS_COLUMN,
                                                        link.getAddr().as5D())
                                                    .query();
        if (query.size() > 0) {
            return query.get(0);
        }
        return null;
    }
}
