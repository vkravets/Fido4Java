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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import org.fidonet.db.DatabasePagingIterator;
import org.fidonet.db.OrmManager;

import java.sql.SQLException;
import java.util.Iterator;

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
        void upgrade(OLD oldObject, NEW newObject);

        void onException(OLD oldObject, Exception ex);
    }

    class Helper {
        public static <OLD, NEW> boolean upgradeObjects(Class<OLD> oldClazz, OrmManager oldManager,
                                                        Class<NEW> newClazz, OrmManager newManager,
                                                        ObjectUpgrader<OLD, NEW> objectUpgrader, boolean failOnError) {
            // old DAO
            Dao<OLD, Object> oldEchoareaDao = oldManager.getDao(oldClazz);

            // new DAO
            Dao<NEW, Object> newEchoareDao = newManager.getDao(newClazz);

            QueryBuilder<OLD, Object> oldObjectQueryBuilder = oldEchoareaDao.queryBuilder();

            Iterator<OLD> linkIterator = new DatabasePagingIterator<OLD, OLD>(oldObjectQueryBuilder);
            while (linkIterator.hasNext()) {
                OLD oldObject = linkIterator.next();
                try {
                    NEW newObject = newClazz.newInstance();
                    objectUpgrader.upgrade(oldObject, newObject);
                    newEchoareDao.create(newObject);
                } catch (InstantiationException e) {
                    objectUpgrader.onException(oldObject, e);
                    if (failOnError) return false;
                } catch (IllegalAccessException e) {
                    objectUpgrader.onException(oldObject, e);
                    if (failOnError) return false;
                } catch (SQLException e) {
                    objectUpgrader.onException(oldObject, e);
                    if (failOnError) return false;
                }
            }
            return true;
        }

    }

}
