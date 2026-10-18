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

import org.fidonet.db.objects.ArchiveReceipt;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.db.objects.MessageReceipt;
import org.fidonet.db.objects.Netmail;
import org.fidonet.db.objects.NetmailDelivery;
import org.fidonet.db.objects.NetmailIndex;
import org.fidonet.db.objects.NetmailRouteRecord;
import org.fidonet.db.objects.Subscription;
import org.fidonet.db.robots.DatabaseRobotRules;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.ArchiveStore;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.echobase.EchoRoutingStore;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.IncrementalEchoStore;
import org.fidonet.echobase.MessageIteration;
import org.fidonet.echobase.NetmailRoute;
import org.fidonet.echobase.NetmailRoutingStore;
import org.fidonet.echobase.NetmailStore;
import org.fidonet.echobase.QueuedNetmail;
import org.fidonet.echobase.StoredMail;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:55 AM
 */
public class MailDatabaseFacade
        implements
            IBase,
            NetmailStore,
            ArchiveStore,
            IncrementalEchoStore,
            NetmailRoutingStore,
            EchoRoutingStore {

    @Override
    public synchronized boolean createEchoFromLink(Link link, String area, AccessLevel access) {
        if (area == null || area.isBlank()
                || (access != AccessLevel.WRITE && access != AccessLevel.BOTH)) {
            throw new IllegalArgumentException("Echo creation requires an area and WRITE or BOTH access");
        }
        String name = area.toLowerCase(Locale.ROOT);
        try {
            return dbManager.inTransaction(() -> {
                if (areas.findByName(name) != null) {
                    return false;
                }
                ConfigurationLink configured = subscriptions.findLink(link);
                if (configured == null) {
                    throw new IllegalArgumentException("Configure the echo link before creating areas");
                }
                if (!areas.createArea(name, null)) {
                    throw new IllegalStateException("Unable to create echo area");
                }
                Subscription subscription = new Subscription();
                subscription.setConfigurationLink(configured);
                subscription.setEchoarea(areas.findByName(name));
                subscription.setLastMessage(0L);
                subscription.setAccessLevel(access);
                dbManager.getDao(Subscription.class).create(subscription);
                if (access == AccessLevel.BOTH) {
                    new EchoRepository(dbManager).initializeConsumer(name,
                                                                     "outbound:" + link.getAddr(),
                                                                     true);
                }
                return true;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create echo area and grant access atomically",
                                            e);
        }
    }

    @Override
    public synchronized void configureEchoSubscription(Link link,
                                                       String area,
                                                       AccessLevel access,
                                                       EchoHistory history) {
        if (access == null || history == null || area == null || area.trim().isEmpty()) {
            throw new IllegalArgumentException("Echo area, access and history policy are required");
        }
        try {
            dbManager.inTransaction(() -> {
                if (subscriptions.findLink(link) == null) {
                    throw new IllegalArgumentException("Configure the echo link before subscribing");
                }
                configureSubscription(link, area, access);
                if (access != AccessLevel.WRITE) {
                    new EchoRepository(dbManager).initializeConsumer(area,
                                                                     "outbound:" + link.getAddr(),
                                                                     history == EchoHistory.NEW_ONLY);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to configure echo routing", e);
        }
    }

    @Override
    public synchronized void removeEchoSubscription(Link link, String area) {
        try {
            ConfigurationLink configured = subscriptions.findLink(link);
            Echoarea echo = areas.findByName(area.toLowerCase(Locale.ROOT));
            if (configured == null || echo == null) {
                return;
            }
            DeleteBuilder<Subscription, Object> delete = dbManager.<Subscription, Object>getDao(Subscription.class)
                                                                  .deleteBuilder();
            delete.where()
                  .eq(Subscription.ID_LINK_COLUMN, configured.getId())
                  .and()
                  .eq(Subscription.ID_AREA_COLUMN, echo.getId());
            delete.delete();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to remove echo subscription", e);
        }
    }

    @Override
    public void visitEchoRange(String area,
                               long firstId,
                               long lastId,
                               MessageIteration.Handler handler)
            throws IOException {
        new EchoRepository(dbManager).visitRange(area, firstId, lastId, handler);
    }

    @Override
    public void visitRecentEcho(String area, int count, MessageIteration.Handler handler)
            throws IOException {
        new EchoRepository(dbManager).visitRecent(area, count, handler);
    }

    @Override
    public List<NetmailRoute> getNetmailRoutes() {
        try {
            List<NetmailRoute> result = new ArrayList<>();
            for (NetmailRouteRecord row : dbManager.<NetmailRouteRecord, String>getDao(NetmailRouteRecord.class)
                                                   .queryBuilder()
                                                   .orderBy("priority", true)
                                                   .orderBy("id", true)
                                                   .query()) {
                result.add(row.toRoute());
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read netmail routes", e);
        }
    }

    @Override
    public synchronized void saveNetmailRoute(NetmailRoute route) {
        try {
            dbManager.getDao(NetmailRouteRecord.class)
                     .createOrUpdate(new NetmailRouteRecord(route));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save netmail route", e);
        }
    }

    @Override
    public synchronized void removeNetmailRoute(String id) {
        try {
            dbManager.<NetmailRouteRecord, String>getDao(NetmailRouteRecord.class).deleteById(id);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to remove netmail route", e);
        }
    }

    @Override
    public void visitNewEcho(String area, String consumer, MessageIteration.Handler handler)
            throws IOException {
        new EchoRepository(dbManager).visitNew(area, consumer, handler);
    }

    private static final Logger logger = LoggerFactory.getLogger(MailDatabaseFacade.class);

    private OrmManager dbManager;

    public <T, ID> Dao<T, ID> extensionDao(Class<T> type) throws SQLException {
        return dbManager.extensionDao(type);
    }

    public <T> T extensionTransaction(Callable<T> operation) throws SQLException {
        return dbManager.inTransaction(operation);
    }
    private final NetmailRepository netmailRepository;
    private final AreaRepository areas;
    private final SubscriptionRepository subscriptions;

    /** Ascending SQL row cursor. Call with limit+1 to determine whether another page exists. */
    public List<StoredMail> readEchoPage(String name, long after, int limit) throws IOException {
        checkPage(after, limit);
        try {
            Echoarea area = areas.findByName(name.toLowerCase(Locale.ROOT));
            if (area == null) {
                throw new IllegalArgumentException("Unknown echo area");
            }
            List<StoredMail> result = new ArrayList<>();
            QueryBuilder<Echomail, Object> query = dbManager.<Echomail, Object>getDao(Echomail.class)
                                                            .queryBuilder()
                                                            .orderBy("id", true)
                                                            .limit((long) limit);
            query.where().eq(Echomail.ID_AREA_COLUMN, area.getId()).and().gt("id", after);
            for (Echomail row : query.query()) {
                row.setArea(area);
                result.add(new StoredMail(row.getId(), row.toMessage()));
            }
            return result;
        } catch (SQLException e) {
            throw new IOException("Unable to read echo page", e);
        }
    }

    public List<StoredMail> readNetmailPage(Collection<FTNAddr> local,
                                            boolean sent,
                                            long after,
                                            int limit)
            throws IOException {
        checkPage(after, limit);
        List<String> addresses = new ArrayList<>();
        for (FTNAddr address : local) {
            addresses.add(address.toString());
        }
        if (addresses.isEmpty()) {
            return List.of();
        }
        try {
            QueryBuilder<Netmail, Object> query = dbManager.<Netmail, Object>getDao(Netmail.class)
                                                           .queryBuilder()
                                                           .orderBy("id", true)
                                                           .limit((long) limit);
            Where<Netmail, Object> where = query.where()
                                                .gt("id", after)
                                                .and()
                                                .in(sent ? "from_addr" : "to_addr", addresses);
            if (!sent) {
                where.and().isNull("route_via");
            }
            List<StoredMail> result = new ArrayList<>();
            for (Netmail row : query.query()) {
                Message message = row.toMessage();
                if (Boolean.TRUE.equals(row.getSend())) {
                    message.setAttributes(message.getAttributes() | 8);
                }
                result.add(new StoredMail(row.getId(), message));
            }
            return result;
        } catch (SQLException e) {
            throw new IOException("Unable to read netmail page", e);
        }
    }

    private static void checkPage(long after, int limit) {
        if (after < 0 || limit < 1 || limit > 201) {
            throw new IllegalArgumentException("Invalid page cursor or size");
        }
    }

    public MailDatabaseFacade(OrmManager manager) {
        this.dbManager = manager;
        netmailRepository = new NetmailRepository(manager);
        areas = new AreaRepository(manager);
        subscriptions = new SubscriptionRepository(manager, areas);
    }

    public DatabaseRobotRules getRobotRules() {
        return new DatabaseRobotRules(dbManager);
    }

    public synchronized void configureLink(Link link, String packetPassword) {
        try {
            Dao<ConfigurationLink, Object> dao = dbManager.getDao(ConfigurationLink.class);
            List<ConfigurationLink> matches = dao.queryForEq(ConfigurationLink.ADDRESS_COLUMN,
                                                             link.getAddr().as5D());
            ConfigurationLink stored = matches.isEmpty() ? new ConfigurationLink() : matches.get(0);
            stored.setAddress(link.getAddr().as5D());
            stored.setPassword(link.getPass() == null ? "" : link.getPass());
            stored.setPacket_password(packetPassword);
            stored.setHost(link.getHostAddress());
            stored.setPort(link.getPort());
            dao.createOrUpdate(stored);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to configure link", e);
        }
    }

    /** Keeps configuration-related SQL changes rollbackable until file publication succeeds. */
    public synchronized void configureLinksAtomically(List<Link> links,
                                                      List<String> packetPasswords,
                                                      Callable<Void> publication)
            throws SQLException {
        dbManager.inTransaction(() -> {
            for (int index = 0; index < links.size(); index++) {
                configureLink(links.get(index), packetPasswords.get(index));
            }
            return publication.call();
        });
    }

    @Override
    public synchronized boolean importArchive(Message message, String source) {
        if (!message.hasValidAddressing()) {
            throw new IllegalArgumentException("Archive message has invalid addressing");
        }
        try {
            return dbManager.inTransaction(() -> {
                String identity = (message.isEchomail() ? "echo:" : "netmail:")
                                  + MailIdentity.of(message);
                Dao<MessageReceipt, String> receipts = dbManager.getDao(MessageReceipt.class);
                if (receipts.queryForId(identity) != null) {
                    return false;
                }
                if (message.isEchomail() && isDupe(message)) {
                    return false;
                }
                if (!message.isEchomail() && netmailRepository.contains(message)) {
                    return false;
                }
                if (message.isEchomail()) {
                    storeArchiveEcho(message);
                } else {
                    storeArchiveNetmail(message);
                }
                dbManager.getDao(ArchiveReceipt.class).create(new ArchiveReceipt(identity, source));
                receipts.create(new MessageReceipt(identity));
                return true;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to import archive message", e);
        }
    }

    private void storeArchiveEcho(Message message) throws SQLException, ParseException {
        Echoarea area = areas.findByName(message.getArea().toLowerCase(Locale.ROOT));
        if (area == null) {
            throw new IllegalArgumentException("Create the target SQL area before archive import");
        }
        dbManager.getDao(Echomail.class).create(Echomail.fromMessage(message, area));
    }

    private void storeArchiveNetmail(Message message) throws SQLException, ParseException {
        Netmail stored = Netmail.fromMessage(message);
        dbManager.getDao(Netmail.class).create(stored);
        netmailRepository.index(stored);
        NetmailDelivery delivery = new NetmailDelivery("archive:" + MailIdentity.of(message),
                                                       stored,
                                                       null);
        delivery.setState("ARCHIVE");
        dbManager.getDao(NetmailDelivery.class).create(delivery);
    }

    @Override
    public boolean isArchived(Message message) {
        try {
            String identity = (message.isEchomail() ? "echo:" : "netmail:")
                              + MailIdentity.of(message);
            return dbManager.<ArchiveReceipt, String>getDao(ArchiveReceipt.class)
                            .queryForId(identity) != null;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to check archive status", e);
        }
    }

    public synchronized void configureSubscription(Link link, String area, AccessLevel access) {
        String name = area.toLowerCase(Locale.ROOT);
        if (!isAreaExists(name) && !createArea(name)) {
            throw new IllegalStateException("Unable to create echo area " + name);
        }
        addSubscription(link, name);
        try {
            ConfigurationLink configured = subscriptions.findLink(link);
            Echoarea echo = areas.findByName(name);
            UpdateBuilder<Subscription, Object> update = dbManager.<Subscription, Object>getDao(Subscription.class)
                                                                  .updateBuilder();
            update.updateColumnValue(Subscription.ACCESS_LEVEL_COLUMN, access);
            update.where()
                  .eq(Subscription.ID_LINK_COLUMN, configured.getId())
                  .and()
                  .eq(Subscription.ID_AREA_COLUMN, echo.getId());
            if (update.update() == 0) {
                throw new IllegalStateException("Unable to configure echo subscription");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to configure echo subscription", e);
        }
    }

    @Override
    public synchronized void saveNetmail(Message message, String deliveryKey, String nextHop) {
        Dao<NetmailDelivery, String> deliveries = dbManager.getDao(NetmailDelivery.class);
        try {
            dbManager.inTransaction(() -> {
                String identity = "netmail:" + MailIdentity.of(message);
                Dao<MessageReceipt, String> receipts = dbManager.getDao(MessageReceipt.class);
                if (deliveries.queryForId(deliveryKey) == null
                        && receipts.queryForId(identity) == null) {
                    Netmail stored = Netmail.fromMessage(message);
                    stored.setRoute(nextHop);
                    dbManager.getDao(Netmail.class).create(stored);
                    netmailRepository.index(stored);
                    deliveries.create(new NetmailDelivery(deliveryKey, stored, nextHop));
                    receipts.create(new MessageReceipt(identity));
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save netmail", e);
        }
    }

    @Override
    public List<Message> getLocalNetmail(FTNAddr address) {
        try {
            List<Message> result = new ArrayList<>();
            for (NetmailDelivery delivery : dbManager.<NetmailDelivery, String>getDao(NetmailDelivery.class)
                                                     .queryForEq("state", "LOCAL")) {
                if (address.equals(new FTNAddr(delivery.getMessage().getToAddr()))) {
                    result.add(delivery.getMessage().toMessage());
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read netmail", e);
        }
    }

    @Override
    public List<Message> getEditorNetmail(FTNAddr address) {
        List<Message> result = new ArrayList<>();
        try {
            visitEditorNetmail(address, result::add);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read editor netmail", e);
        }
    }

    @Override
    public void visitEditorNetmail(FTNAddr address, MessageIteration.Handler visitor)
            throws IOException {
        netmailRepository.visitEditor(address, visitor);
    }

    @Override
    public void visitQueuedNetmail(NetmailStore.QueuedHandler visitor) throws IOException {
        netmailRepository.visitQueued(visitor);
    }
    @Override
    public List<QueuedNetmail> getQueuedNetmail() {
        try {
            List<QueuedNetmail> result = new ArrayList<>();
            for (NetmailDelivery delivery : dbManager.<NetmailDelivery, String>getDao(NetmailDelivery.class)
                                                     .queryForEq("state", "QUEUED")) {
                result.add(new QueuedNetmail(delivery.getKey(),
                                             delivery.getNextHop(),
                                             delivery.getMessage().toMessage()));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read outgoing netmail", e);
        }
    }

    @Override
    public synchronized void markNetmailSent(String deliveryKey) {
        try {
            dbManager.inTransaction(() -> {
                Dao<NetmailDelivery, String> deliveries = dbManager.getDao(NetmailDelivery.class);
                NetmailDelivery delivery = deliveries.queryForId(deliveryKey);
                acknowledgeDelivery(delivery, deliveries);
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acknowledge outgoing netmail", e);
        }
    }

    private void acknowledgeDelivery(NetmailDelivery delivery,
                                     Dao<NetmailDelivery, String> deliveries)
            throws SQLException {
        if (delivery == null) {
            return;
        }
        if ((delivery.getMessage().getAttr() & 128) != 0) {
            deliveries.delete(delivery);
            dbManager.getDao(NetmailIndex.class).deleteById(delivery.getMessage().getId());
            dbManager.getDao(Netmail.class).delete(delivery.getMessage());
            return;
        }
        delivery.setState("SENT");
        delivery.getMessage().setSend(true);
        dbManager.getDao(Netmail.class).update(delivery.getMessage());
        deliveries.update(delivery);
    }

    @Override
    public boolean open() {
        try {
            if (!dbManager.isConnect()) {
                dbManager.connect();
            }
            dbManager.createTables();
        } catch (SQLException | IllegalArgumentException e) {
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
        return areas.createArea(areaName, description);
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
    public Iterator<Message> getMessages(Link link,
                                         String areaname,
                                         long startMessage,
                                         long bundleSize) {
        return getMessages(link, areaname, startMessage, bundleSize, false);
    }

    @Override
    public Message getMessage(String area, String id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            Echoarea echoarea = areas.findByName(area);
            if (echoarea == null) {
                return null;
            }
            List<Echomail> query = echomailQueryBuilder.where()
                                                       .eq(Echomail.ID_AREA_COLUMN,
                                                           echoarea.getId())
                                                       .and()
                                                       .eq(Echomail.MSGID_COLUMN, id)
                                                       .query();
            if (query.size() > 0) {
                return query.get(0).toMessage();
            }
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
            List<Echomail> query = echomailQueryBuilder.where()
                                                       .eq(Echomail.MSGID_COLUMN, id)
                                                       .query();
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
            Echoarea echoarea = areas.findByName(areaname);
            if (echoarea == null) {
                return -1;
            }
            QueryBuilder<Echomail, Object> queryBuilder = echomails.queryBuilder();
            queryBuilder.setCountOf(true).where().eq(Echomail.ID_AREA_COLUMN, echoarea.getId());
            return echomails.countOf(queryBuilder.prepare());
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public synchronized void addMessage(Message message, String areaname) {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        Dao<Echomail, Object> echomailDao = dbManager.getDao(Echomail.class);
        try {
            dbManager.inTransaction(() -> {
                String identity = "echo:" + MailIdentity.of(message);
                Dao<MessageReceipt, String> receipts = dbManager.getDao(MessageReceipt.class);
                if (receipts.queryForId(identity) != null) {
                    return null;
                }
                Echoarea area = areas.findByName(areaname);
                if (area == null) {
                    // TODO: Check autocreate flag
                    area = new Echoarea();
                    area.setName(areaname);
                    echoareasDao.create(area);
                }
                Echomail echomail = Echomail.fromMessage(message, area);
                // TODO: if my point - check access level, if it's not allow to post - sent to him
                // netmail with such info
                echomailDao.create(echomail);
                receipts.create(new MessageReceipt(identity));
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save message in " + areaname, e);
        }
    }

    public List<String> getAreas() {
        return areas.getAreas();
    }

    public boolean isAreaExists(String name) {
        return areas.isAreaExists(name);
    }

    @Override
    public void setLinkLastMessage(Link link, String areaname, Long id) {
        subscriptions.setLinkLastMessage(link, areaname, id);
    }

    @Override
    public boolean isDupe(Message message) {
        Dao<Echomail, Object> daoEchomail = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> daoEchoarea = dbManager.getDao(Echoarea.class);
        try {
            QueryBuilder<Echoarea, Object> echoareaQueryBuilder = daoEchoarea.queryBuilder();
            if (dbManager.<MessageReceipt, String>getDao(MessageReceipt.class)
                         .queryForId("echo:" + MailIdentity.of(message)) != null) {
                return true;
            }
            echoareaQueryBuilder.where()
                                .eq(Echoarea.NAME_COLUMN,
                                    message.getArea().toLowerCase(Locale.ROOT));

            QueryBuilder<Echomail, Object> echomailObjectQueryBuilder = daoEchomail.queryBuilder();
            echomailObjectQueryBuilder.join(echoareaQueryBuilder)
                                      .setCountOf(true)
                                      .where()
                                      .eq(Echomail.MSGID_COLUMN,
                                          message.getMsgId() == null
                                                  ? "NO-MSGID:" + MailIdentity.of(message)
                                                  : message.getMsgId());
            return daoEchomail.countOf(echomailObjectQueryBuilder.prepare()) != 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to check duplicates", e);
        }
    }

    @Override
    public List<String> getSubscriptions(Link link) {
        return subscriptions.getSubscriptions(link);
    }

    @Override
    public void addSubscription(Link link, String area) {
        subscriptions.addSubscription(link, area);
    }

    @Override
    public void close() {
        dbManager.disconnect();
    }

    @Override
    public boolean canReadEcho(Link link, String area) {
        return subscriptions.hasAccess(link, area, AccessLevel.READ);
    }

    public synchronized Map<String, AccessLevel> getEchoAccess(Link link) {
        return subscriptions.accessForLink(link);
    }

    @Override
    public boolean canWriteEcho(Link link, String area) {
        return subscriptions.hasAccess(link, area, AccessLevel.WRITE);
    }

    /** * Private stuff *** */
    private Iterator<Message> createEchomailIterator(final QueryBuilder<Echomail, Object> echomailQueryBuilder,
                                                     long startMessage,
                                                     long bundleSize,
                                                     boolean continueQuery) {
        if (bundleSize > DatabasePagingIterator.MESSAGE_LIMIT_QUERY) {
            logger.warn("Bundle size for getting messages cannot be greater then {}. Using this value.",
                        DatabasePagingIterator.MESSAGE_LIMIT_QUERY);
            bundleSize = DatabasePagingIterator.MESSAGE_LIMIT_QUERY;
        }
        return new DatabasePagingIterator<Echomail, Message>(echomailQueryBuilder,
                                                             startMessage,
                                                             bundleSize,
                                                             continueQuery) {
            @Override
            public Message convert(Echomail echomail) {
                return echomail.toMessage();
            }
        };
    }

    public Iterator<Message> getMessages(String areaname,
                                         long startMessage,
                                         long bundleSize,
                                         boolean continueQuery) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            Echoarea echoarea = areas.findByName(areaname);
            if (echoarea == null) {
                logger.warn("{} area is not exists! Skipping getting messages...",
                            areaname,
                            new Throwable());
                return Collections.emptyIterator();
            }
            echomailQueryBuilder.orderBy(Echomail.ID_COLUMN, true)
                                .where()
                                .eq(Echomail.ID_AREA_COLUMN, echoarea.getId());

            return createEchomailIterator(echomailQueryBuilder,
                                          startMessage,
                                          bundleSize,
                                          continueQuery);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }

    public Iterator<Message> getMessages(Link link,
                                         String areaname,
                                         long startMessage,
                                         long bundleSize,
                                         boolean continueQuery) {
        Dao<Echomail, Object> echomailsDao = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomailsDao.queryBuilder();

        try {
            ConfigurationLink configurationLink = subscriptions.findLink(link);
            if (configurationLink == null) {
                logger.warn("{} link is not found! Skipping getting messages...",
                            link.getAddr().as5D());
                return Collections.emptyIterator();
            }

            List<Subscription> subscriptionList = subscriptions.forLink(configurationLink);
            if (subscriptionList.size() == 0) {
                logger.warn("{} link haven't any subscription! Skipping getting messages...",
                            link.getAddr().as5D());
                return Collections.emptyIterator();
            }

            echomailQueryBuilder.orderBy(Echomail.ID_COLUMN, true);
            Where<Echomail, Object> where = echomailQueryBuilder.where();
            int branches = 0;
            for (Subscription subscription : subscriptionList) {
                if (areaname == null
                        || subscription.getEchoarea().getName().equalsIgnoreCase(areaname)) {
                    Long lastMessageID = subscription.getLastMessage();
                    where.eq(Echomail.ID_AREA_COLUMN, subscription.getEchoarea().getId())
                         .and()
                         .gt(Echomail.ID_COLUMN, lastMessageID == null ? 0L : lastMessageID);
                    branches++;
                }
            }
            if (branches == 0) {
                return Collections.emptyIterator();
            }
            if (branches > 1) {
                where.or(branches);
            }

            return createEchomailIterator(echomailQueryBuilder,
                                          startMessage,
                                          bundleSize,
                                          continueQuery);

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Collections.emptyIterator();
    }
}
