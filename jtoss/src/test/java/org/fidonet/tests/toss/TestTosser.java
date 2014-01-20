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

import junit.framework.TestCase;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
public class TestTosser {

    private OrmManager ormManager;
    private DatabaseManager databaseManager;

    private static File computeTestDataRoot(Class anyTestClass) {
        final String clsUri = anyTestClass.getName().replace('.', '/') + ".class";
        final URL url = anyTestClass.getClassLoader().getResource(clsUri);
        final String clsPath = url.getPath();
        final File root = new File(clsPath.substring(0, clsPath.length() - clsUri.length()));
        return new File(root.getParentFile(), "test-data");
    }


    @Before
    public void setUp() throws SQLException {
        File pathDb = computeTestDataRoot(TestTosser.class);
        System.setProperty("derby.stream.error.file", pathDb + "/derby.log");
        ormManager = new OrmManager("jdbc:derby:" + pathDb + "/TestDerby;create=true");
        databaseManager = new DatabaseManager(ormManager);
        databaseManager.open();
    }

    @After
    public void tearDown() throws SQLException {
        ormManager.dropTables();
        databaseManager.close();
    }


    @Test
    public void testToss() {
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
            TestCase.assertEquals(8, list.size());
            TestCase.assertEquals(true, echoMgr.isEchoExists("ru.anime"));
            TestCase.assertEquals(false, echoMgr.isEchoExists("ru.cracks"));
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
}
