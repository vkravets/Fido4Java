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

package org.fidonet.tests.toss;

import com.j256.ormlite.dao.Dao;
import junit.framework.TestCase;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.Message;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 5:42 PM
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTosser {

    private static OrmManager ormManager;
    private static DatabaseManager databaseManager;

    private static File computeTestDataRoot(Class anyTestClass) {
        final String clsUri = anyTestClass.getName().replace('.', '/') + ".class";
        final URL url = anyTestClass.getClassLoader().getResource(clsUri);
        final String clsPath = url.getPath();
        final File root = new File(clsPath.substring(0, clsPath.length() - clsUri.length()));
        return new File(root.getParentFile(), "test-data");
    }


    @BeforeClass
    public static void setUp() throws SQLException {
        File pathDb = computeTestDataRoot(TestTosser.class);
//        System.setProperty("derby.stream.error.file", pathDb + "/derby.log");
//        ormManager = new OrmManager("jdbc:derby:" + pathDb + "/TestDerby;create=true");
//        ormManager = new OrmManager("jdbc:h2:" + pathDb + "/TestH2;TRACE_LEVEL_FILE=4");
        ormManager = new OrmManager("jdbc:h2:" + pathDb + "/TestH2");
        databaseManager = new DatabaseManager(ormManager);
        databaseManager.open();
    }

    @AfterClass
    public static void tearDown() throws SQLException {
        ormManager.dropTables();
        databaseManager.close();
    }


    @Test
    public void testFlowToss() {
        JFtnConfig config = new JFtnConfig();
        InputStream configStream = TestTosser.class.getClassLoader().getResourceAsStream("jftn.conf");

        try {
            config.load(configStream);
        } catch (ParseConfigException e) {
            TestCase.fail(e.getMessage());
        }

        try {
            Tosser tosser = new Tosser(config, databaseManager);
            tosser.runFast(config.getValue("Inbound"));
            EchoMgr echoMgr = tosser.getAreamgr();
            List<String> list = echoMgr.getEchos();
            System.out.println(list);
            TestCase.assertEquals(8, list.size());
            TestCase.assertEquals(true, echoMgr.isEchoExists("ru.anime"));
            TestCase.assertEquals(false, echoMgr.isEchoExists("ru.cracks"));
            TestCase.assertEquals(4, databaseManager.getMessageSize("ru.anime"));
            Iterator<Message> messages = databaseManager.getMessages("ru.anime");
            while (messages.hasNext()) {
                Message message = messages.next();
                System.out.println("**************************************");
                System.out.println(message.toString());
                System.out.println(message.getMessageCharset());
                Charset charset = CharsetTools.charsetDetect(message.getMessageCharset());
                CharBuffer b = charset.decode(ByteBuffer.wrap(message.getBody().getBytes(charset)));
                String body = new String(b.array());
                System.out.println(body.replaceAll("\\u0001", "@").replaceAll("\\r", "\r\n"));
                System.out.println("++++++++++++++++++++++++++++++++++++++");
            }
        } catch (Exception e) {
            System.out.println("Test Failed. Details: " + e.getMessage());
            e.printStackTrace(System.out);
            TestCase.fail();
        }
    }

    @Test
    public void testGenerationMessagesToLink() throws SQLException {
        ConfigurationLink link = new ConfigurationLink();
        link.setAddress("2:467/1313.0@fidonet.org");
        link.setHost("localhost");
        link.setPassword("pass");
        link.setPacket_password("pass");
        link.setFlags("BINKP");
        Dao<ConfigurationLink, Object> linksDao = ormManager.getDao(ConfigurationLink.class);
        linksDao.create(link);

        long messageSize = databaseManager.getMessageSize("ru.anime");
        long messageSize1 = databaseManager.getMessageSize("ru.golded");
        long messageSize2 = databaseManager.getMessageSize("ru.computerra");
        System.out.println(String.format("ru.anime: %d", messageSize));
        System.out.println(String.format("ru.golded: %d", messageSize1));
        System.out.println(String.format("ru.computerra: %d", messageSize2));

        Iterator<Message> messages = databaseManager.getMessages(link.toLink());
        int num = 0;
        while (messages.hasNext()) {
            messages.next();
            num++;
        }

        System.out.println("Link don't have any subscription");
        System.out.println("Checking that there no any messages for link.");
        TestCase.assertEquals(0, num);

        databaseManager.addSubscription(link.toLink(), "ru.anime");
        System.out.println("Subscribed to ru.anime successfully.");

        messages = databaseManager.getMessages(link.toLink());
        num = 0;
        while (messages.hasNext()) {
            messages.next();
            num++;
        }
        System.out.println("Checking correctness of messages number...");

        TestCase.assertEquals(4, num);

        databaseManager.addSubscription(link.toLink(), "ru.golded");
        System.out.println("Subscribed to ru.golded successfully.");

        num = 0;
        messages = databaseManager.getMessages(link.toLink());
        while (messages.hasNext()) {
            messages.next();
            num++;
        }
        System.out.println("Checking correctness of messages number...");
        TestCase.assertEquals(5, num);

        databaseManager.addSubscription(link.toLink(), "ru.computerra");
        System.out.println("Subscribed to ru.computerra successfully.");

        num = 0;
        messages = databaseManager.getMessages(link.toLink());
        while (messages.hasNext()) {
            messages.next();
            num++;
        }
        System.out.println("Checking correctness of messages number...");
        TestCase.assertEquals(43, num);

        System.out.println("Checking behavior with last message id algorithm...");
        databaseManager.setLinkLastMessage(link.toLink(), "ru.anime", 10L);
        databaseManager.setLinkLastMessage(link.toLink(), "ru.computerra", 9L);
        databaseManager.setLinkLastMessage(link.toLink(), "ru.golded", 14L);

        num = 0;
        messages = databaseManager.getMessages(link.toLink());
        while (messages.hasNext()) {
            messages.next();
            num++;
        }
        System.out.println("Checking correctness of messages number...");
        TestCase.assertEquals(36, num);

        System.out.println("SUCCESS");
    }
}
