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

package org.fidonet.tests.toss;

import junit.framework.TestCase;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.jftn.tosser.Tosser;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 5:42 PM
 */
public class TestTosser {

    @Test
    public void testToss() {
        JFtnConfig config = new JFtnConfig();
        InputStream configStream = TestTosser.class.getClassLoader().getResourceAsStream("jftn.conf");

        try {
            config.load(configStream);
        } catch (ParseConfigException e) {
            TestCase.fail(e.getMessage());
        }

        try{
            Tosser tosser = new Tosser(config);
            tosser.runFast(config.getValue("Inbound"));
            EchoMgr echoMgr = tosser.getAreamgr();
            List<String> list = echoMgr.getEchos();
            TestCase.assertEquals(8, list.size());
            TestCase.assertEquals(true, echoMgr.isEchoExists("ru.anime"));
            TestCase.assertEquals(false, echoMgr.isEchoExists("ru.cracks"));
            // TODO: test echobase
        } catch (Exception e) {
            System.out.println("Test Failed. Details: " + e.getMessage());
            e.printStackTrace(System.out);
            TestCase.fail();
        }



    }
}
