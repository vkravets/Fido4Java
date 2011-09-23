package org.fidonet.tests.share;

import junit.framework.TestCase;
import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.engine.script.ScriptManager;
import org.fidonet.jftn.event.Event;
import org.fidonet.jftn.event.EventHandler;
import org.fidonet.jftn.event.HasEventBus;
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
 * To change this template use File | Settings | File Templates.
 */
public class TestHook extends HasEventBus {

    @Before
    public void setupEnv() throws Exception {
        // Init ScriptManager
        ScriptManager scriptManager = new ScriptManager();
        // Init hook and command classes
        HookInterpreter hookInterpreter = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        // Add to script scope "jftn" variable which have all above data
        scriptManager.addScriptVar("jftn", new JFtnShare(scriptManager, hookInterpreter, commandInterpreter));

        InputStream inputStream = ScriptManager.class.getClassLoader().getResourceAsStream("testHook.py");
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
