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

package org.fidonet.tests.share;

import junit.framework.TestCase;
import org.fidonet.events.Event;
import org.fidonet.events.EventHandler;
import org.fidonet.events.HasEventBus;
import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.engine.script.JythonScriptManager;
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
public class TestHook extends HasEventBus {

    @Before
    public void setupEnv() throws Exception {
        // Init JythonScriptManager
        ScriptEngine scriptManager = new JythonScriptManager();
        // Init hook and command classes
        HookInterpreter hookInterpreter = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        // Add to script scope "jftn" variable which have all above data
        scriptManager.addScriptVar("jftn", new JFtnShare(scriptManager, hookInterpreter, commandInterpreter));

        InputStream inputStream = JythonScriptManager.class.getClassLoader().getResourceAsStream("testHook.py");
        scriptManager.runScript(inputStream);
    }

    @After
    public void tearDown() {
        getEventBus().clear();
    }

    @Test
    public void testHookRegistering() throws Exception {
        TestCase.assertEquals(1, getEventBus().getAllListeners(TestEvent.class).size());

        getEventBus().register(NullEvent.class, new EventHandler() {
            @Override
            public void onEventHandle(Event event) {
                TestCase.assertEquals(true, event instanceof NullEvent);
            }
        });
        TestCase.assertEquals(1, getEventBus().getAllListeners(TestEvent.class).size());
        TestCase.assertEquals(1, getEventBus().getAllListeners(NullEvent.class).size());

        PrintStream console = System.out;
        ConsoleOutputStream consoleMonitor = new ConsoleOutputStream();
        System.setOut(new PrintStream(consoleMonitor, true));
        getEventBus().notify(new NullEvent());
        System.out.flush();
        System.setOut(console);
        TestCase.assertEquals("", consoleMonitor.getBuffer());
    }

    @Test
    public void testHookExecute() throws Exception {
        PrintStream console = System.out;
        ConsoleOutputStream consoleMonitor = new ConsoleOutputStream();
        System.setOut(new PrintStream(consoleMonitor, true));
        getEventBus().notify(new TestEvent("test"));
        System.out.flush();
        System.setOut(console);
        TestCase.assertEquals("TestHook test", consoleMonitor.getBuffer());
    }
}
