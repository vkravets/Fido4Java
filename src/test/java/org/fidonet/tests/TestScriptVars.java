package org.fidonet.tests;

import junit.framework.TestCase;
import org.fidonet.jftn.engine.script.ScriptManager;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.junit.Test;

import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 5:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestScriptVars extends CommandInterpreter {

    @Test
    public void testCommandRegister() throws Exception {
        ScriptManager scriptManager = ScriptManager.getInstance();
        InputStream inputStream = ScriptManager.class.getClassLoader().getResourceAsStream("testScriptVars.py");
        TestScriptObject test = new TestScriptObject();
        try {

            scriptManager.addScriptVar("jftn", test);
            scriptManager.runScript(inputStream);
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        Command command = CommandCollection.getInstance().findCommandByName("test");
        TestCase.assertNotNull(command);

        command.execute(null);
        TestCase.assertEquals("testVar", test.getVar());
    }

}
