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
import org.fidonet.db.objects.Subscription;
import org.fidonet.db.objects.Version;
import org.fidonet.types.FTNAddr;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.jdbc.JdbcDatabaseConnection;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:59 AM
 */
public class OrmManager {

    public static final Logger logger = LoggerFactory.getLogger(OrmManager.class);

    private String jdbcUrl;
    private String user = null;
    private String password = null;

    protected ConnectionSource connectionSource;

    private Map<Class<?>, Dao<?, ?>> daoMap;

    protected Set<Class<?>> daoClasses;

    protected void setDaoClassesList() {
        daoClasses = new HashSet<Class<?>>();
        daoClasses.add(Echoarea.class);
        daoClasses.add(Echomail.class);
        daoClasses.add(ConfigurationLink.class);
        daoClasses.add(Netmail.class);
        daoClasses.add(NetmailDelivery.class);
        daoClasses.add(NetmailIndex.class);
        daoClasses.add(MessageReceipt.class);
        daoClasses.add(ArchiveReceipt.class);
        daoClasses.add(Subscription.class);
        daoClasses.add(Version.class);
    }

    public OrmManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        setDaoClassesList();
    }

    public OrmManager(String jdbcUrl, String user, String password) {
        this(jdbcUrl);
        this.user = user;
        this.password = password;
    }

    public void connect() throws SQLException {
        if (isConnect()) {
            return;
        }
        if (user != null && password != null && user.length() > 0 && password.length() > 0) {
            connectionSource = new JdbcConnectionSource(jdbcUrl, user, password);
        } else {
            connectionSource = new JdbcConnectionSource(jdbcUrl);
        }

        try {
            daoMap = new HashMap<Class<?>, Dao<?, ?>>();
            for (Class<?> daoClass : daoClasses) {
                daoMap.put(daoClass, DaoManager.createDao(connectionSource, daoClass));
            }
            createTables();
            if (daoClasses.contains(Netmail.class)) {
                removeLegacyNetmailSendIndex();
                addEchoIngressColumn();
                migrateLegacyNetmailDeliveries();
                new NetmailRepository(this).indexExisting();
            }
        } catch (SQLException | RuntimeException e) {
            try {
                connectionSource.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            } finally {
                connectionSource = null;
                daoMap = null;
            }
            throw e;
        }
    }

    public boolean isConnect() {
        return connectionSource != null && connectionSource.isOpen();
    }

    /** Upgrade the legacy, incorrectly unique Boolean index without rebuilding mail rows. */
    private void removeLegacyNetmailSendIndex() throws SQLException {
        DatabaseConnection database = connectionSource.getReadWriteConnection();
        try {
            Connection jdbc = ((JdbcDatabaseConnection) database).getInternalConnection();
            Set<String> indexes = legacySendIndexes(jdbc);
            String quote = jdbc.getMetaData().getIdentifierQuoteString().trim();
            for (String name : indexes) {
                dropIndex(jdbc, name, quote);
            }
        } finally {
            connectionSource.releaseConnection(database);
        }
    }

    private Set<String> legacySendIndexes(Connection jdbc) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (ResultSet tables = jdbc.getMetaData()
                                    .getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (!"netmail".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    continue;
                }
                indexes.addAll(sendOnlyIndexes(jdbc,
                                               tables.getString("TABLE_SCHEM"),
                                               tables.getString("TABLE_NAME")));
            }
        }
        return indexes;
    }

    private Set<String> sendOnlyIndexes(Connection jdbc, String schema, String table)
            throws SQLException {
        Map<String, Set<String>> columns = uniqueIndexColumns(jdbc, schema, table);
        Set<String> indexes = new HashSet<>();
        for (Map.Entry<String, Set<String>> index : columns.entrySet()) {
            if (index.getValue().equals(Collections.singleton("send"))) {
                indexes.add(index.getKey());
            }
        }
        return indexes;
    }

    private Map<String, Set<String>> uniqueIndexColumns(Connection jdbc,
                                                        String schema,
                                                        String table)
            throws SQLException {
        Map<String, Set<String>> columns = new HashMap<>();
        try (ResultSet rows = jdbc.getMetaData().getIndexInfo(null, schema, table, true, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name != null && column != null) {
                    columns.computeIfAbsent(name, ignored -> new HashSet<>())
                           .add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private void dropIndex(Connection jdbc, String name, String quote) throws SQLException {
        try (Statement statement = jdbc.createStatement()) {
            statement.executeUpdate("DROP INDEX "
                                    + quote
                                    + name.replace(quote, quote + quote)
                                    + quote);
        }
    }

    private void addEchoIngressColumn() throws SQLException {
        DatabaseConnection database = connectionSource.getReadWriteConnection();
        try {
            Connection jdbc = ((JdbcDatabaseConnection) database).getInternalConnection();
            addEchoIngressColumns(jdbc);
        } finally {
            connectionSource.releaseConnection(database);
        }
    }

    private void addEchoIngressColumns(Connection jdbc) throws SQLException {
        try (ResultSet tables = jdbc.getMetaData()
                                    .getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if ("echomail".equalsIgnoreCase(table)) {
                    ensureEchoIngressColumn(jdbc, tables.getString("TABLE_SCHEM"), table);
                }
            }
        }
    }

    private void ensureEchoIngressColumn(Connection jdbc, String schema, String table)
            throws SQLException {
        if (!hasEchoIngressColumn(jdbc, schema, table)) {
            getDao(Echomail.class).executeRaw("ALTER TABLE "
                                              + table
                                              + " ADD received_from VARCHAR(255)");
        }
    }

    private boolean hasEchoIngressColumn(Connection jdbc, String schema, String table)
            throws SQLException {
        boolean found = false;
        try (ResultSet columns = jdbc.getMetaData().getColumns(null, schema, table, null)) {
            while (columns.next()) {
                found |= "received_from".equalsIgnoreCase(columns.getString("COLUMN_NAME"));
            }
        }
        return found;
    }

    private void migrateLegacyNetmailDeliveries() throws SQLException {
        inTransaction(() -> {
            Dao<NetmailDelivery, String> deliveries = getDao(NetmailDelivery.class);
            Dao<Netmail, Long> messages = getDao(Netmail.class);
            for (Netmail message : messages.queryBuilder()
                                           .where()
                                           .notIn("id",
                                                  deliveries.queryBuilder()
                                                            .selectColumns("message_id"))
                                           .query()) {
                deliveries.create(legacyDelivery(message));
            }
            return null;
        });
    }

    private NetmailDelivery legacyDelivery(Netmail message) {
        String route = message.getRoute();
        if (route != null && route.trim().isEmpty()) {
            route = null;
        }
        NetmailDelivery delivery = new NetmailDelivery("legacy:" + message.getId(), message, route);
        if (Boolean.TRUE.equals(message.getSend())) {
            delivery.setState("SENT");
        } else if (route != null && !isValidLegacyRoute(route)) {
            delivery.setState("BLOCKED");
        }
        return delivery;
    }

    private boolean isValidLegacyRoute(String route) {
        try {
            return new FTNAddr(route).isValid();
        } catch (RuntimeException invalidRoute) {
            return false;
        }
    }
    public <T> T inTransaction(Callable<T> work) throws SQLException {
        return TransactionManager.callInTransaction(connectionSource, work);
    }

    public void createTables() throws SQLException {
        for (Dao<?, ?> dao : daoMap.values()) {
            if (!dao.isTableExists()) {
                TableUtils.createTable(connectionSource, dao.getDataClass());
            }
        }
    }

    public void dropTables() throws SQLException {
        for (Dao<?, ?> dao : daoMap.values()) {
            if (dao.isTableExists()) {
                TableUtils.dropTable(connectionSource, dao.getDataClass(), true);
            }
        }
    }

    public <T, ID> Dao<T, ID> getDao(Class<T> clazz) {
        return (Dao<T, ID>) daoMap.get(clazz);
    }

    public <T extends Dao<?, ?>> Set<T> getAllDaos() {
        Set<T> result = new HashSet<T>();
        for (Class<?> daoClass : daoClasses) {
            result.add((T) daoMap.get(daoClass));
        }
        return result;
    }

    public void initDefaults() {
    }

    public void disconnect() {
        if (connectionSource != null) {
            try {
                connectionSource.close();
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public Long getCurrentVersion() {
        Dao<Version, Object> dao = getDao(Version.class);
        try {
            Version curVersion = dao.queryBuilder()
                                    .orderBy(Version.ID_COLUMN, false)
                                    .limit(1L)
                                    .queryForFirst();
            if (curVersion == null) {
                logger.error("Any version cannot be found, cannot continue",
                             new Throwable("Version table is corrupted"));
                return Version.UNKNOWN_VERSION;
            }
            return curVersion.getVersion();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Version.UNKNOWN_VERSION;
    }

    public boolean setCurrentVersion(Version version) {
        Dao<Version, Object> dao = getDao(Version.class);
        try {
            // TODO: Maybe change to create only?
            // because there is a small chance that we will need to update version... (???)
            Dao.CreateOrUpdateStatus status = dao.createOrUpdate(version);
            return status.isCreated() || status.isUpdated();
        } catch (SQLException e) {
            logger.error("Cannot set new version {}", version.getVersion(), e);
            return false;
        }
    }
}
