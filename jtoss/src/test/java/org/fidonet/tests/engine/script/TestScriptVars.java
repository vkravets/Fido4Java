package org.fidonet.tests.engine.script;

import junit.framework.TestCase;
import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.engine.script.ScriptManager;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.junit.Test;

import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 5:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestScriptVars {

    @Test
    public void testCommandRegister() throws Exception {
        // Init ScriptManager
        ScriptManager scriptManager = new ScriptManager();
        // Init hook and command classes
        HookInterpreter hookInterpreter = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        // Add to script scope "jftn" variable which have all above data
        scriptManager.addScriptVar("jftn", new JFtnShare(scriptManager, hookInterpreter, commandInterpreter));

        InputStream inputStream = ScriptManager.class.getClassLoader().getResourceAsStream("testScriptVars.py");
        TestScriptObject test = new TestScriptObject();
        try {

            scriptManager.addScriptVar("testScriptVar", test);
            scriptManager.runScript(inputStream);
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        Command command = commands.findCommandByName("test");
        TestCase.assertNotNull(command);

        command.execute(null);
        TestCase.assertEquals("testVar", test.getVar());
    }

}
