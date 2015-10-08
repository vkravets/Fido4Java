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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import junit.framework.TestCase;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 10:53 AM
 */
public class CreateDatabaseTest {

    private static final File pathDb = TestUtils.computeTestDataRoot(CreateDatabaseTest.class);
    private static OrmManager ormManager;
    private static DatabaseManager databaseManager;
    private static Dao<ConfigurationLink, Long> daoLinks;
    private static ConfigurationLink configurationLink;

    @BeforeClass
    public static void setUp() throws SQLException {
//        System.setProperty("derby.stream.error.file", pathDb + "/derby.log");
//        ormManager = new OrmManager("jdbc:derby:" + pathDb + "/TestDerby;create=true");
//        ormManager = new OrmManager("jdbc:h2:" + pathDb + "/TestH2;TRACE_LEVEL_FILE=4");
        ormManager = new OrmManager("jdbc:h2:" + pathDb + "/TestH2");
        databaseManager = new DatabaseManager(ormManager);
        databaseManager.open();
        daoLinks = ormManager.getDao(ConfigurationLink.class);

        configurationLink = new ConfigurationLink();
        configurationLink.setAddress("2:467/110.1@fidonet.org");
        configurationLink.setPassword("P@ssw0rd");
        configurationLink.setPacket_password("P@ssw0rd");
        configurationLink.setFlags("BINKD");
        daoLinks.create(configurationLink);
    }

    @AfterClass
    public static void tearDown() throws SQLException {
        ormManager.dropTables();
        databaseManager.close();
    }

    @Test
    public void testDatabaseCreation() {
        try {

            QueryBuilder<ConfigurationLink, Long> linkQueryBuilder = daoLinks.queryBuilder();

            List<ConfigurationLink> address = linkQueryBuilder.where().eq("address", "2:467/110.1@fidonet.org").query();
            TestCase.assertEquals(1, address.size());
        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testMessagesSelection() {
        databaseManager.getMessages("test");
    }

    private void addArea(String area) {
        databaseManager.createArea(area);
    }

    private void addTestSubscription(Link link, String area) {
        databaseManager.addSubscription(link, area);
    }

    @Test
    public void testSubscriptionAdding() {
        addArea("test1");
        addArea("test2");
        addArea("test3");
        addArea("test111");
        addArea("test4");
        addTestSubscription(configurationLink.toLink(), "test1");
        addTestSubscription(configurationLink.toLink(), "test111");

        Link link2 = new Link(new FTNAddr("2:467/110.23@fidonet.org"), null, "pass", "localhost", 1010);
        ConfigurationLink configurationLink2 = new ConfigurationLink();
        configurationLink2.setAddress(link2.getAddr().as5D());
        configurationLink2.setPacket_password(link2.getPass());
        configurationLink2.setPassword(link2.getPass());
        configurationLink2.setHost(link2.getHostAddress());
        configurationLink2.setPort(link2.getPort());
        try {
            daoLinks.create(configurationLink2);
        } catch (SQLException e) {
            e.printStackTrace();
            TestCase.fail("Error during creating link: " + e.getMessage());
        }
        addTestSubscription(link2, "test4");

        List<String> subscriptions1 = databaseManager.getSubscriptions(configurationLink.toLink());
        TestCase.assertEquals(2, subscriptions1.size());
        TestCase.assertEquals(Arrays.asList("test1", "test111"), subscriptions1);

        List<String> subscriptions2 = databaseManager.getSubscriptions(link2);
        TestCase.assertEquals(1, subscriptions2.size());
        TestCase.assertEquals(Arrays.asList("test4"), subscriptions2);

    }


}
