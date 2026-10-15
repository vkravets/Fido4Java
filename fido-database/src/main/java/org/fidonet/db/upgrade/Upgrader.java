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

package org.fidonet.db.upgrade;

import org.fidonet.db.DatabasePagingIterator;
import org.fidonet.db.OrmManager;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.QueryBuilder;

import java.lang.reflect.Field;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 2/1/14
 * Time: 2:15 PM
 */
public interface Upgrader {

    boolean upgrade(OrmManager oldManager, OrmManager newManager);

    long getVersion();

    interface ObjectUpgrader<OLD, NEW> {
        void upgrade(OLD oldObject, NEW newObject) throws SQLException;

        void onException(OLD oldObject, Exception ex);
    }

    class Helper {
        public static <OLD, NEW> boolean upgradeObjects(Class<OLD> oldClazz,
                                                        OrmManager oldManager,
                                                        Class<NEW> newClazz,
                                                        OrmManager newManager,
                                                        ObjectUpgrader<OLD, NEW> objectUpgrader,
                                                        boolean failOnError) {
            // old DAO
            Dao<OLD, Object> oldEchoareaDao = oldManager.getDao(oldClazz);

            // new DAO
            Dao<NEW, Object> newEchoareDao = newManager.getDao(newClazz);

            QueryBuilder<OLD, Object> oldObjectQueryBuilder = oldEchoareaDao.queryBuilder();
            orderByIdentity(oldClazz, oldObjectQueryBuilder);

            try (DatabasePagingIterator<OLD, OLD> linkIterator = new DatabasePagingIterator<OLD, OLD>(oldObjectQueryBuilder,
                                                                                                      0,
                                                                                                      DatabasePagingIterator.MESSAGE_LIMIT_QUERY,
                                                                                                      true)) {
                while (linkIterator.hasNext()) {
                    OLD oldObject = linkIterator.next();
                    boolean upgraded = upgradeObject(oldObject,
                                                     newClazz,
                                                     newEchoareDao,
                                                     objectUpgrader);
                    if (!upgraded && failOnError) {
                        return false;
                    }
                }
                return true;
            } catch (SQLException e) {
                DatabasePagingIterator.logger.error("Unable to close migration iterator", e);
                return false;
            }
        }

        private static <OLD> void orderByIdentity(Class<OLD> type,
                                                  QueryBuilder<OLD, Object> query) {
            for (Field field : type.getDeclaredFields()) {
                DatabaseField metadata = field.getAnnotation(DatabaseField.class);
                if (metadata == null || !metadata.id() && !metadata.generatedId()) {
                    continue;
                }
                query.orderBy(metadata.columnName().isEmpty()
                        ? field.getName()
                        : metadata.columnName(), true);
                return;
            }
        }

        private static <OLD, NEW> boolean upgradeObject(OLD oldObject,
                                                        Class<NEW> type,
                                                        Dao<NEW, Object> target,
                                                        ObjectUpgrader<OLD, NEW> upgrader) {
            try {
                NEW newObject = type.newInstance();
                upgrader.upgrade(oldObject, newObject);
                target.create(newObject);
                return true;
            } catch (InstantiationException | IllegalAccessException | SQLException e) {
                upgrader.onException(oldObject, e);
                return false;
            }
        }
    }
}
