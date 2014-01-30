/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.db;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.db.objects.Subscription;
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
    private static final Long MESSAGE_LIMIT_QUERY = 500L;

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
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        try {
            List<Echoarea> echoareaList = echoareaQueryBuilder.selectColumns("id", "name").where().eq("name", areaname).query();
            if (echoareaList.size() == 0) {
                return Collections.emptyIterator();
            }
            Where<Echomail, Object> echomainWhere = echomailQueryBuilder.where().eq("id_echoarea", echoareaList.get(0).getId());
            return new WhereDatabaseLimitIterator<Echomail, Message>(echomails, echomainWhere, -1, MESSAGE_LIMIT_QUERY, true) {
                @Override
                public Message convert(Echomail echomail) {
                    return echomail.toMessage();
                }
            };
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Message> getMessages(String areaname, long startMessage, long bundleSize) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        try {
            List<Echoarea> echoareaList = echoareaQueryBuilder.selectColumns("id", "name").where().eq("name", areaname).query();
            if (echoareaList.size() == 0) {
                return Collections.emptyIterator();
            }
            Where<Echomail, Object> echomainWhere = echomailQueryBuilder.where().eq("id_echoarea", echoareaList.get(0).getId());
            return new WhereDatabaseLimitIterator<Echomail, Message>(echomails, echomainWhere, startMessage, bundleSize, false) {
                @Override
                public Message convert(Echomail echomail) {
                    return echomail.toMessage();
                }
            };
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Message> getMessages(Link link) {
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        Dao<Echomail, Object> echomailsDao = dbManager.getDao(Echomail.class);
        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder();
        QueryBuilder<Subscription, Object> subscriptionsQueryBuilder = subscriptionsDao.queryBuilder();
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomailsDao.queryBuilder();

        try {
            List<ConfigurationLink> linkList = linksQueryBuilder.selectColumns("id", "address").where().eq("address", link.getAddr().as5D()).query();
            if (linkList.size() == 0) {
                return Collections.emptyIterator();
            }

            List<Subscription> subscriptionList = subscriptionsQueryBuilder.where().eq("id_link", linkList.get(0).getId()).query();
            if (subscriptionList.size() == 0) {
                return Collections.emptyIterator();
            }

            Long minLastMessage = Long.MAX_VALUE;
            List<Long> areas_ids = new ArrayList<Long>();
            for (Subscription subscription : subscriptionList) {
                Long lastMessageID = subscription.getLastMessage();
                if (lastMessageID < minLastMessage)
                    minLastMessage = lastMessageID;
                areas_ids.add(subscription.getEchoarea().getId());
            }

            Where<Echomail, Object> ge = echomailQueryBuilder.where().in("id_echoarea", areas_ids).and().gt("id", minLastMessage);

            return new WhereDatabaseLimitIterator<Echomail, Message>(echomailsDao, ge, -1, MESSAGE_LIMIT_QUERY, true) {
                @Override
                public Message convert(Echomail object) {
                    return object.toMessage();
                }
            };

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Message> getMessages(Link link, String areaname) {
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        Dao<Echomail, Object> echomailsDao = dbManager.getDao(Echomail.class);
        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder();
        QueryBuilder<Subscription, Object> subscriptionsQueryBuilder = subscriptionsDao.queryBuilder();
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomailsDao.queryBuilder();

        try {
            List<ConfigurationLink> linkList = linksQueryBuilder.selectColumns("id", "address").where().eq("address", link.getAddr().as5D()).query();
            if (linkList.size() == 0) {
                return Collections.emptyIterator();
            }

            List<Subscription> subscriptionList = subscriptionsQueryBuilder.where().eq("id_link", linkList.get(0).getId()).query();
            if (subscriptionList.size() == 0) {
                return Collections.emptyIterator();
            }

            Long minLastMessage = Long.MAX_VALUE;
            List<Long> areas_ids = new ArrayList<Long>();
            for (Subscription subscription : subscriptionList) {
                Long lastMessageID = subscription.getLastMessage();
                if (lastMessageID < minLastMessage)
                    minLastMessage = lastMessageID;
                if ((subscription.getEchoarea().getName().equals(areaname))) {
                    areas_ids.add(subscription.getEchoarea().getId());
                }
            }

            Where<Echomail, Object> ge = echomailQueryBuilder.where().in("id_echoarea", areas_ids).and().gt("id", minLastMessage);

            return new WhereDatabaseLimitIterator<Echomail, Message>(echomailsDao, ge, 0, MESSAGE_LIMIT_QUERY, true) {
                @Override
                public Message convert(Echomail object) {
                    return object.toMessage();
                }
            };

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Message> getMessages(Link link, String areaname, long startMessage, long bundleSize) {
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        Dao<Echomail, Object> echomailsDao = dbManager.getDao(Echomail.class);
        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder();
        QueryBuilder<Subscription, Object> subscriptionsQueryBuilder = subscriptionsDao.queryBuilder();
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomailsDao.queryBuilder();

        try {
            List<ConfigurationLink> linkList = linksQueryBuilder.selectColumns("id", "address").where().eq("address", link.getAddr().as5D()).query();
            if (linkList.size() == 0) {
                return Collections.emptyIterator();
            }

            List<Subscription> subscriptionList = subscriptionsQueryBuilder.where().eq("id_link", linkList.get(0).getId()).query();
            if (subscriptionList.size() == 0) {
                return Collections.emptyIterator();
            }

            Long minLastMessage = Long.MAX_VALUE;
            List<Long> areas_ids = new ArrayList<Long>();
            for (Subscription subscription : subscriptionList) {
                Long lastMessageID = subscription.getLastMessage();
                if (lastMessageID < minLastMessage)
                    minLastMessage = lastMessageID;
                if ((subscription.getEchoarea().getName().equals(areaname))) {
                    areas_ids.add(subscription.getEchoarea().getId());
                }
            }

            Where<Echomail, Object> ge = echomailQueryBuilder.where().in("id_echoarea", areas_ids).and().gt("id", minLastMessage);

            return new WhereDatabaseLimitIterator<Echomail, Message>(echomailsDao, ge, startMessage, bundleSize, false) {
                @Override
                public Message convert(Echomail object) {
                    return object.toMessage();
                }
            };

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Message getMessage(String area, int id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        try {
            List<Echoarea> echoareaList = echoareaQueryBuilder.selectColumns("id", "name").where().eq("name", area).query();
            if (echoareaList.size() == 0) {
                return null;
            }
            List<Echomail> query = echomailQueryBuilder.where().eq("id_echoarea", echoareaList.get(0).getId()).and().eq("id", id).query();
            if (query.size() > 0)
                return query.get(0).toMessage();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Message getMessage(int id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            List<Echomail> query = echomailQueryBuilder.where().eq("id", id).query();
            if (query.size() > 0)
                return query.get(0).toMessage();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public long getMessageSize(String areaname) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        try {
            List<Echoarea> echoareaList = echoareaQueryBuilder.selectColumns("id", "name").where().eq("name", areaname).query();
            if (echoareaList.size() == 0) {
                return 0;
            }
            return echomails.countOf(echomails.queryBuilder().setCountOf(true).where().eq("id_echoarea", echoareaList.get(0).getId()).prepare());
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
            List<Echoarea> echoareas = echoareasDao.queryBuilder().selectColumns("id", "name").where().eq("name", areaname).query();
            Echoarea area;
            if (echoareas.size() > 0) {
                area = echoareas.get(0);
            } else {
                // TODO: Check autocreate flag
                area = new Echoarea();
                area.setName(areaname);
                echoareasDao.create(area);
            }
            Echomail echomail = Echomail.fromMessage(message, area);
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
            List<Echoarea> echoareaList = echoareasDao.queryBuilder().selectColumns("name").query();
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
            return echoareasDao.countOf(echoareasDao.queryBuilder().setCountOf(true).where().eq("name", name).prepare()) == 1;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void setLinkLastMessage(Link link, String areaname, Long id) {
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        Dao<Echomail, Object> echomailsDao = dbManager.getDao(Echomail.class);
        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder();
        QueryBuilder<Subscription, Object> subscriptionsQueryBuilder = subscriptionsDao.queryBuilder();
        QueryBuilder<Echomail, Object> echomailsQueryBuilder = echomailsDao.queryBuilder();
        try {
            List<ConfigurationLink> links = linksQueryBuilder.where().eq("address", link.getAddr().as5D()).query();
            if (links.size() != 0) {
                List<Echomail> echomailList = echomailsQueryBuilder.where().eq("name", areaname).query();
                if (echomailList.size() > 0) {
                    List<Subscription> subscriptionList = subscriptionsQueryBuilder.where().eq("id_echoarea", echomailList.get(0).getId()).query();
                    if (subscriptionList.size() > 0) subscriptionList.get(0).setLastMessage(id);
                }
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
            echoareaQueryBuilder.where().eq("name", message.getArea().toLowerCase());
            Where<Echomail, Object> msgid = daoEchomail.queryBuilder().join(echoareaQueryBuilder).setCountOf(true).where().eq("msgId", message.getMsgId());
            return daoEchomail.countOf(msgid.prepare()) != 0;
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
        QueryBuilder<ConfigurationLink, Object> linksQueryBuilder = linksDao.queryBuilder().selectColumns("id", "address");
        QueryBuilder<Echoarea, Object> echoareasQueryBuilder = echoareasDao.queryBuilder().selectColumns("id", "name");
        try {
            linksQueryBuilder.selectColumns("id").where().eq("address", link.getAddr().as5D());
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
        return Collections.EMPTY_LIST;
    }

    @Override
    public void addSubscription(Link link, String area) {
        List<String> subscriptions = getSubscriptions(link);
        if (subscriptions.contains(area.toLowerCase())) return;
        Dao<ConfigurationLink, Object> linksDao = dbManager.getDao(ConfigurationLink.class);
        Dao<Echoarea, Object> echosDao = dbManager.getDao(Echoarea.class);
        Dao<Subscription, Object> subscriptionsDao = dbManager.getDao(Subscription.class);
        try {
            List<ConfigurationLink> addressList = linksDao.queryBuilder().where().eq("address", link.getAddr().as5D()).query();
            if (addressList.size() == 0) return;
            ConfigurationLink configurationLink = addressList.get(0);

            List<Echoarea> names = echosDao.queryBuilder().where().eq("name", area.toLowerCase()).query();
            if (names.size() == 0) return;
            Echoarea echoarea = names.get(0);

            Subscription subscription = new Subscription();
            subscription.setConfigurationLink(configurationLink);
            subscription.setEchoarea(echoarea);
            subscription.setLastMessage(0L);
            subscription.setAccessLevel(Subscription.AccessLevel.BOTH);
            subscriptionsDao.create(subscription);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        dbManager.disconnect();
    }
}
