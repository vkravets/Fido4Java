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

package org.fidonet.tests.share;

import junit.framework.TestCase;
import org.fidonet.events.HasEventBus;
import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.fidonet.jftn.engine.script.JFtnScriptService;
import org.fidonet.jftn.engine.script.ScriptEngine;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.fidonet.tests.tools.ConsoleOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 4:42 PM
 */
public class TestHookGroovy extends HasEventBus {

    private InputStream hookScript = TestHookGroovy.class.getClassLoader().getResourceAsStream("testHook.groovy");
    private ScriptEngine scriptManager;
    private JFtnScriptService serviceAPI;

    @Before
    public void setupEnv() throws Exception {
        // Init hook and command classes
        scriptManager = new GroovyScriptManager();
        HookInterpreter hookInterpreter = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        // Add to script scope "jftn" variable which have all above data
        serviceAPI = new JFtnScriptService(scriptManager, hookInterpreter, commandInterpreter);
        scriptManager.registerScript(hookScript, serviceAPI);
    }

    @After
    public void tearDown() throws Exception {
        scriptManager.unregisterScript(hookScript, serviceAPI);
//        hookScript.reset();
    }

    @Test
    public void testHookRegistering() throws Exception {
//        TestCase.assertEquals(1, getEventBus().getAllListeners(TestEvent.class).size());

        getEventBus().subscribe(new NullEventHandler() {
            @Override
            public void onEventHandle(NullEvent event) {

            }
        });

//        TestCase.assertEquals(1, getEventBus().getAllListeners(TestEvent.class).size());
//        TestCase.assertEquals(1, getEventBus().getAllListeners(NullEvent.class).size());

        PrintStream console = System.out;
        ConsoleOutputStream consoleMonitor = new ConsoleOutputStream();
        System.setOut(new PrintStream(consoleMonitor, true));
        getEventBus().publish(new NullEvent());
        System.out.flush();
        System.setOut(console);
        TestCase.assertEquals("", consoleMonitor.getBuffer());
    }

    @Test
    public void testHookExecute() throws Exception {
        PrintStream console = System.out;
        ConsoleOutputStream consoleMonitor = new ConsoleOutputStream();
        System.setOut(new PrintStream(consoleMonitor, true));
        getEventBus().publish(new TestEvent("test"));
        Thread.sleep(500);
        System.out.flush();
        System.setOut(console);
        TestCase.assertEquals("TestHook test", consoleMonitor.getBuffer());
    }
}
