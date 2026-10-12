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

import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.stmt.QueryBuilder;
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
public class DatabasePagingIterator<T, K> implements Iterator<K>, AutoCloseable {

    public static final Logger logger = LoggerFactory.getLogger(DatabasePagingIterator.class);

    public static final Long MESSAGE_LIMIT_QUERY = 1000L;


    private long limit;
    private long offset;
    private CloseableIterator<T> curlist;
    private QueryBuilder<T, Object> objectQueryBuilder;
    private boolean continueQuery;
    private boolean closed;
    private long pageCount;

    public DatabasePagingIterator(QueryBuilder<T, Object> queryBuilder) {
        this(queryBuilder, -1, MESSAGE_LIMIT_QUERY, false);
    }

    public DatabasePagingIterator(QueryBuilder<T, Object> queryBuilder, long offset, long limit) {
        this(queryBuilder, offset, limit, false);
    }

    public DatabasePagingIterator(QueryBuilder<T, Object> queryBuilder, long offset, long limit, boolean continueQuery) {
        this.limit = limit;
        if (offset < -1 || limit == 0 || limit < -1) {
            throw new IllegalArgumentException("Invalid paging offset or limit");
        }
        this.offset = Math.max(0, offset);
        objectQueryBuilder = queryBuilder;
        this.continueQuery = continueQuery;
    }

    @Override
    public boolean hasNext() {
        if (closed) return false;
        try {
            if (curlist != null && curlist.hasNext()) return true;
            if (curlist == null || continueQuery && limit > 0 && pageCount == limit) {
                queryDb();
            } else {
                // need to close iterator, since amount of queried message are less then limit
                close();
                return false;
            }
            return curlist != null && curlist.hasNext();
        } catch (SQLException e) {
            closeAfterFailure(e);
            logger.error(e.getMessage(), e);
            throw new IllegalStateException("Unable to read database page", e);
        } catch (RuntimeException e) {
            closeAfterFailure(e);
            throw e;
        }
    }

    private void queryDb() throws SQLException {
        closeCurrentIterator();
        objectQueryBuilder.limit(limit > 0 ? Long.valueOf(limit) : null);
        objectQueryBuilder.offset(limit > 0 && offset > 0 ? Long.valueOf(offset) : null);
        curlist = objectQueryBuilder.iterator();
        // Some databases require LIMIT with OFFSET. An unlimited query skips
        // the requested prefix using its cursor instead of inventing a limit.
        if (limit < 0) {
            long skipped = 0;
            while (skipped < offset && curlist.hasNext()) {
                curlist.next();
                skipped++;
            }
        }
        pageCount = 0;
        if (!curlist.hasNext()) {
            close();
        }
    }

    @Override
    public K next() {
        if (hasNext()) {
            offset++;
            pageCount++;
            try {
                return convert(curlist.next());
            } catch (RuntimeException e) {
                closeAfterFailure(e);
                throw e;
            }
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

    @Override
    public void close() throws SQLException {
        closed = true;
        closeCurrentIterator();
    }

    private void closeCurrentIterator() throws SQLException {
        CloseableIterator<T> iterator = curlist;
        curlist = null;
        if (iterator != null) iterator.close();
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (SQLException closeError) {
            failure.addSuppressed(closeError);
        }
    }

}
