/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.db;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import junit.framework.Assert;
import org.fidonet.db.objects.Link;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 10:53 AM
 */
public class CreateDatabaseTest {

    private OrmManager ormManager;
    private DatabaseManager databaseManager;
    private Dao<Link,Long> daoLinks;

    @Before
    public void setUp() throws SQLException {
        File pathDb = TestUtils.computeTestDataRoot(CreateDatabaseTest.class);
        System.setProperty("derby.stream.error.file", pathDb + "/derby.log");
        ormManager = new OrmManager("jdbc:derby:"+pathDb+"/TestDerby;create=true");
        ormManager.connect();
        ormManager.createTable();
        databaseManager = new DatabaseManager(ormManager);
        daoLinks = ormManager.getDao(Link.class);
    }

    @After
    public void tearDown() throws SQLException {
        ormManager.disconnect();
    }

    @Test
    public void testDatabaseCreation() {
        try {
            Link link = new Link();
            link.setAddress("2:467/110.1@fidonet.org");
            link.setPassword("P@ssw0rd");
            link.setPacket_password("P@ssw0rd");
            link.setFlags("BINKD");
            daoLinks.create(link);

            QueryBuilder<Link,Long> linkQueryBuilder = daoLinks.queryBuilder();

            List<Link> address = linkQueryBuilder.where().eq("address", "2:467/110.1@fidonet.org").query();
            Assert.assertEquals(1, address.size());
            Assert.assertTrue(daoLinks.objectsEqual(link, address.get(0)));
            DeleteBuilder<Link,Long> deleteBuilder = daoLinks.deleteBuilder();
            deleteBuilder.where().eq("id", link.getId());
            deleteBuilder.delete();
        } catch (SQLException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testMessagesSelection() {
        databaseManager.getMessages("test");
    }


}
