/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.db;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.db.objects.Subscription;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.IBase;
import org.fidonet.types.Link;
import org.fidonet.types.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:55 AM
 */
public class DatabaseManager implements IBase {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private OrmManager dbManager;

    public DatabaseManager(OrmManager manager) {
        this.dbManager = manager;
    }

    @Override
    public boolean open() {
        try {
            if (!dbManager.isConnect()) {
                dbManager.connect();
            }
            dbManager.createTables();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public boolean createArea(String areaName) {
        return this.createArea(areaName, null);
    }

    @Override
    public boolean createArea(String areaName, String description) {
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        Echoarea echoarea = new Echoarea();
        echoarea.setName(areaName);
        if (description != null)
            echoarea.setDescription(description);
        try {
            echoareas.create(echoarea);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public Iterator<Message> getMessages(String areaname) {
        return getMessages(areaname, -1, DatabasePagingIterator.MESSAGE_LIMIT_QUERY, true);
    }

    @Override
    public Iterator<Message> getMessages(String areaname, long startMessage, long bundleSize) {
        return getMessages(areaname, startMessage, bundleSize, false);
    }

    @Override
    public Iterator<Message> getMessages(Link link) {
        return getMessages(link, null, -1, DatabasePagingIterator.MESSAGE_LIMIT_QUERY, true);
    }

    @Override
    public Iterator<Message> getMessages(Link link, String areaname) {
        return getMessages(link, areaname, -1, DatabasePagingIterator.MESSAGE_LIMIT_QUERY, true);
    }

    @Override
    public Iterator<Message> getMessages(Link link, String areaname, long startMessage, long bundleSize) {
        return getMessages(link, areaname, startMessage, bundleSize, false);
    }

    @Override
    public Message getMessage(String area, String id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            Echoarea echoarea = findEchoareaByName(area);
            if (echoarea == null) {
                return null;
            }
            List<Echomail> query = echomailQueryBuilder.
                    where().
                    eq(Echomail.ID_AREA_COLUMN, echoarea.getId()).
                    and().
                    eq(Echomail.MSGID_COLUMN, id).
                    query();
            if (query.size() > 0)
                return query.get(0).toMessage();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Message> getMessage(String id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            List<Echomail> query = echomailQueryBuilder.where().eq(Echomail.MSGID_COLUMN, id).query();
            List<Message> result = new ArrayList<Message>(query.size());
            for (Echomail echomail : query) {
                result.add(echomail.toMessage());
            }
            return result;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public long getMessageSize(String areaname) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        try {
            Echoarea echoarea = findEchoareaByName(areaname);
            if (echoarea == null) {
                return -1;
            }
            QueryBuilder<Echomail, Object> queryBuilder = echomails.queryBuilder();
            queryBuilder.setCountOf(true).
                    where().
                    eq(Echomail.ID_AREA_COLUMN, echoarea.getId());
            return echomails.countOf(queryBuilder.prepare());
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public void addMessage(Message message, String areaname) {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        Dao<Echomail, Object> echomailDao = dbManager.getDao(Echomail.class);
        try {
            Echoarea area = findEchoareaByName(areaname);
            if (area == null) {
                // TODO: Check autocreate flag
                area = new Echoarea();
                area.setName(areaname);
                echoareasDao.create(area);
            }
            Echomail echomail = Echomail.fromMessage(message, area);
            // TODO: if my point - check access level, if it's not allow to post - sent to him netmail with such info
            echomailDao.create(echomail);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } catch (ParseException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public List<String> getAreas() {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        List<String> result = new ArrayList<String>();
        try {
            List<Echoarea> echoareaList = echoareasDao.queryBuilder().selectColumns(Echoarea.NAME_COLUMN).query();
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
            echoareaQueryBuilder.
                    setCountOf(true).
                    where().eq(Echoarea.NAME_COLUMN, name);
            return echoareasDao.countOf(echoareaQueryBuilder.prepare()) == 1;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void setLinkLastMessage(Link link, String areaname, Long id) {
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        QueryBuilder<Subscription, Object> subscriptionsQueryBuilder = subscriptionsDao.queryBuilder();

        try {
            ConfigurationLink configurationLink = findConfigurationLinkByLink(link);
            if (configurationLink == null) {
                logger.warn("Link {} is not found, skip adding subscription", link.getAddr().as5D());
                return;
            }

            Echoarea echoarea = findEchoareaByName(areaname);
            if (echoarea == null) {
                logger.warn("Trying to change last message id for non-existing area ({}) for {} link", areaname, link.getAddr().as5D());
                return;
            }

            Where<Subscription, Object> subscriptionObjectWhere = subscriptionsQueryBuilder.
                    where().
                    eq(Subscription.ID_AREA_COLUMN, echoarea.getId()).
                    and().
                    eq(Subscription.ID_LINK_COLUMN, configurationLink.getId());
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

    @Override
    public boolean isDupe(Message message) {
        Dao<Echomail, Object> daoEchomail = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> daoEchoarea = dbManager.getDao(Echoarea.class);
        try {
            QueryBuilder<Echoarea, Object> echoareaQueryBuilder = daoEchoarea.queryBuilder();
            echoareaQueryBuilder.where().eq(Echoarea.NAME_COLUMN, message.getArea().toLowerCase());

            QueryBuilder<Echomail, Object> echomailObjectQueryBuilder = daoEchomail.queryBuilder();
            echomailObjectQueryBuilder.
                    join(echoareaQueryBuilder).
                    setCountOf(true).
                    where().eq(Echomail.MSGID_COLUMN, message.getMsgId());
            return daoEchomail.countOf(echomailObjectQueryBuilder.prepare()) != 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public List<String> getSubscriptions(Link link) {
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        Dao<Echoarea, Object> echoareasDao = dbManager.getDao(Echoarea.class);

        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder().
                selectColumns(ConfigurationLink.ID_COLUMN, ConfigurationLink.ADDRESS_COLUMN);
        QueryBuilder<Echoarea, Object> echoareasQueryBuilder = echoareasDao.queryBuilder().
                selectColumns(Echoarea.ID_COLUMN, Echoarea.NAME_COLUMN);

        try {
            linksQueryBuilder.selectColumns(ConfigurationLink.ID_COLUMN).
                    where().eq(ConfigurationLink.ADDRESS_COLUMN, link.getAddr().as5D());

            QueryBuilder<Subscription, Object> joinLinks = subscriptionsDao.queryBuilder().join(linksQueryBuilder);
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

    @Override
    public void addSubscription(Link link, String area) {
        List<String> subscriptions = getSubscriptions(link);
        if (subscriptions.contains(area.toLowerCase())) return;

        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        try {
            ConfigurationLink configurationLink = findConfigurationLinkByLink(link);
            if (configurationLink == null) {
                logger.warn("Link {} is not found, skip adding subscription", link.getAddr().as5D());
                return;
            }

            Echoarea echoarea = findEchoareaByName(area);
            if (echoarea == null) {
                logger.warn("Trying adding subscription to {} link for non-existing are ({})", link.getAddr().as5D(), area);
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

    @Override
    public void close() {
        dbManager.disconnect();
    }

    /**
     * * Private stuff ***
     */

    private Iterator<Message> createEchomailIterator(final QueryBuilder<Echomail, Object> echomailQueryBuilder, long startMessage, long bundleSize, boolean continueQuery) {
        if (bundleSize > DatabasePagingIterator.MESSAGE_LIMIT_QUERY) {
            logger.warn("Bundle size for getting messages cannot be greater then {}. Using this value.", DatabasePagingIterator.MESSAGE_LIMIT_QUERY);
            bundleSize = DatabasePagingIterator.MESSAGE_LIMIT_QUERY;
        }
        return new DatabasePagingIterator<Echomail, Message>(echomailQueryBuilder, startMessage, bundleSize, continueQuery) {
            @Override
            public Message convert(Echomail echomail) {
                return echomail.toMessage();
            }
        };
    }

    private List<Subscription> getSubscriptionsForLink(ConfigurationLink confLink) throws SQLException {
        Dao<Subscription, Object> dao = dbManager.getDao(Subscription.class);
        QueryBuilder<Subscription, Object> queryBuilder = dao.queryBuilder();
        return queryBuilder.
//                selectColumns(Subscription.ID_AREA_COLUMN, Subscription.ID_LINK_COLUMN, Subscription.LASTMESSAGE_ID_COLUMN).
        where().eq(Subscription.ID_LINK_COLUMN, confLink.getId()).
                query();
    }


    private ConfigurationLink findConfigurationLinkByLink(Link link) throws SQLException {
        Dao<ConfigurationLink, Object> dao = dbManager.getDao(ConfigurationLink.class);
        QueryBuilder<ConfigurationLink, Object> queryBuilder = dao.queryBuilder();
        List<ConfigurationLink> query = queryBuilder.
                selectColumns(ConfigurationLink.ID_COLUMN, ConfigurationLink.ADDRESS_COLUMN).
                where().eq(ConfigurationLink.ADDRESS_COLUMN, link.getAddr().as5D()).
                query();
        if (query.size() > 0)
            return query.get(0);
        return null;
    }

    private Echoarea findEchoareaByName(String areaname) throws SQLException {
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        List<Echoarea> query = echoareaQueryBuilder.
                selectColumns(Echoarea.ID_COLUMN, Echoarea.NAME_COLUMN).
                where().eq(Echoarea.NAME_COLUMN, areaname).
                query();
        if (query.size() > 0)
            return query.get(0);
        return null;
    }

    public Iterator<Message> getMessages(String areaname, long startMessage, long bundleSize, boolean continueQuery) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            Echoarea echoarea = findEchoareaByName(areaname);
            if (echoarea == null) {
                logger.warn("{} area is not exists! Skipping getting messages...", areaname, new Throwable());
                return Collections.emptyIterator();
            }
            echomailQueryBuilder.orderBy(Echomail.ID_COLUMN, true).
                    where().eq(Echomail.ID_AREA_COLUMN, echoarea.getId());

            return createEchomailIterator(echomailQueryBuilder, startMessage, bundleSize, continueQuery);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    public Iterator<Message> getMessages(Link link, String areaname, long startMessage, long bundleSize, boolean continueQuery) {
        Dao<Echomail, Object> echomailsDao = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomailsDao.queryBuilder();

        try {
            ConfigurationLink configurationLink = findConfigurationLinkByLink(link);
            if (configurationLink == null) {
                logger.warn("{} link is not found! Skipping getting messages...", link.getAddr().as5D());
                return Collections.emptyIterator();
            }

            List<Subscription> subscriptionList = getSubscriptionsForLink(configurationLink);
            if (subscriptionList.size() == 0) {
                logger.warn("{} link haven't any subscription! Skipping getting messages...", link.getAddr().as5D());
                return Collections.emptyIterator();
            }

            Long minLastMessage = Long.MAX_VALUE;
            List<Long> areas_ids = new ArrayList<Long>();
            for (Subscription subscription : subscriptionList) {
                Long lastMessageID = subscription.getLastMessage();
                if (lastMessageID < minLastMessage)
                    minLastMessage = lastMessageID;
                if (areaname == null || (subscription.getEchoarea().getName().equals(areaname))) {
                    areas_ids.add(subscription.getEchoarea().getId());
                }
            }

            echomailQueryBuilder.orderBy(Echomail.ID_COLUMN, true).
                    where().
                    in(Echomail.ID_AREA_COLUMN, areas_ids).
                    and().
                    gt(Echomail.ID_COLUMN, minLastMessage);

            return createEchomailIterator(echomailQueryBuilder, startMessage, bundleSize, continueQuery);

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

}
