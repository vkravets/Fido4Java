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

import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/29/14
 * Time: 2:54 PM
 */
public class WhereDatabaseLimitIterator<T, K> implements Iterator<K> {

    public static final Logger logger = LoggerFactory.getLogger(WhereDatabaseLimitIterator.class);

    private Where<T, Object> echomailObjectWhere;
    private long limit;
    private long offset;
    private CloseableIterator<T> curlist;
    private QueryBuilder<T, Object> objectQueryBuilder;
    private boolean continueQuery;

    public WhereDatabaseLimitIterator(Dao<T, Object> echomails, Where<T, Object> echomainWhere, long offset, long limit) {
        this(echomails, echomainWhere, offset, limit, false);
    }

    public WhereDatabaseLimitIterator(Dao<T, Object> echomails, Where<T, Object> echomainWhere, long offset, long limit, boolean continueQuery) {
        this.echomailObjectWhere = echomainWhere;
        this.limit = limit;
        this.offset = offset;
        objectQueryBuilder = echomails.queryBuilder();
        this.continueQuery = continueQuery;
    }

    @Override
    public boolean hasNext() {
        if (curlist != null && curlist.hasNext()) return true;
        try {
            if (curlist == null || continueQuery && offset >= limit) {
                queryDb();
            } else {
                return false;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return curlist != null && curlist.hasNext();
    }

    private void queryDb() throws SQLException {
        if (curlist != null) curlist.close();
        if (limit > -1) {
            objectQueryBuilder = objectQueryBuilder.limit(limit);
        }
        if (offset > -1) {
            objectQueryBuilder = objectQueryBuilder.offset(offset);
        }
        if (echomailObjectWhere != null) {
            objectQueryBuilder.setWhere(echomailObjectWhere);
            curlist = objectQueryBuilder.iterator();
        } else {
            curlist = objectQueryBuilder.iterator();
        }
        if (!curlist.hasNext()) {
            curlist.close();
        }
    }

    @Override
    public K next() {
        if (hasNext()) {
            offset++;
            return convert(curlist.next());
        }
        throw new NoSuchElementException();
    }

    public K convert(T object) {
        return (K) object;
    }

    @Override
    public void remove() {
        curlist.remove();
    }

}
