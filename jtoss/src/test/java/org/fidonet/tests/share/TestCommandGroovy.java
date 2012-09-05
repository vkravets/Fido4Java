package org.fidonet.tests.share;

import junit.framework.TestCase;
import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.engine.script.ScriptEngine;
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
public class TestCommandGroovy {

    @Test
    public void testCommandRegister() throws Exception {
        // Init JythonScriptManager
        ScriptEngine scriptManager = new GroovyScriptManager();
        // Init hook and command classes
        HookInterpreter hookInterpreter = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        // Add to script scope "jftn" variable which have all above data
        scriptManager.addScriptVar("jftn", new JFtnShare(scriptManager, hookInterpreter, commandInterpreter));

        InputStream inputStream = ScriptEngine.class.getClassLoader().getResourceAsStream("testCommand.groovy");
        try {
            scriptManager.runScript(inputStream);
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        Command<String[], Object> command = commands.findCommandByName("test");
        TestCase.assertNotNull(command);
        String[] param = new String[]{"test", "test2"};
        Object result = command.execute(param);
        TestCase.assertEquals(param, result);

    }

}
