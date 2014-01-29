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

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/29/14
 * Time: 2:54 PM
 */
public class WhereDatabaseLimitIterator<T, K> implements Iterator<K> {

    private Where<T, Object> echomailObjectWhere;
    private long limit;
    private long offset;
    private Iterator<T> curlist;
    private QueryBuilder<T, Object> objectQueryBuilder;

    public WhereDatabaseLimitIterator(Dao<T, Object> echomails, Where<T, Object> echomainWhere, long i) {
        this.echomailObjectWhere = echomainWhere;
        this.limit = i;
        this.offset = 0;
        objectQueryBuilder = echomails.queryBuilder();
    }

    @Override
    public boolean hasNext() {
        if (curlist != null && curlist.hasNext()) return true;
        try {
            queryDb();
        } catch (SQLException e) {
            // todo logger
            return false;
        }
        return curlist != null && curlist.hasNext();
    }

    private void queryDb() throws SQLException {
        objectQueryBuilder = objectQueryBuilder.limit(limit).offset(offset);
        objectQueryBuilder.setWhere(echomailObjectWhere);
        List<T> query = objectQueryBuilder.query();
        curlist = query.iterator();
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
