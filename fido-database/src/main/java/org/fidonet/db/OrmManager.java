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
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.fidonet.db.objects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:59 AM
 */
public class OrmManager {

    public static final Logger logger = LoggerFactory.getLogger(OrmManager.class);
    public static final long UNKNOWN_VERSION = -1;

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

        daoMap = new HashMap<Class<?>, Dao<?, ?>>();

        for (Class<?> daoClass : daoClasses) {
            daoMap.put(daoClass, DaoManager.createDao(connectionSource, daoClass));
        }

        createTables();
    }

    public boolean isConnect() {
        return connectionSource != null && connectionSource.isOpen();
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
        if (connectionSource != null)
            try {
                connectionSource.close();
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
            }
    }


    public Long getCurrentVersion() {
        Dao<Version, Object> dao = getDao(Version.class);
        try {
            Version curVersion = dao.queryBuilder().orderBy(Version.ID_COLUMN, false).limit(1L).queryForFirst();
            if (curVersion == null) {
                logger.error("Any version cannot be found, cannot continue", new Throwable("Version table is corrupted"));
                return UNKNOWN_VERSION;
            }
            return curVersion.getVersion();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return UNKNOWN_VERSION;
    }

    public boolean setCurrentVersion(Version version) {
        Dao<Version, Object> dao = getDao(Version.class);
        try {
            // TODO: Maybe change to create only?
            // because there is a little chance that we need to update version... (???)
            Dao.CreateOrUpdateStatus status = dao.createOrUpdate(version);
            return status.isCreated() || status.isUpdated();
        } catch (SQLException e) {
            logger.error("Cannot set new version {}", version.getVersion(), e);
            return false;
        }
    }
}
