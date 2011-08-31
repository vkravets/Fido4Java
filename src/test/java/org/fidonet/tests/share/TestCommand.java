package org.fidonet.tests.share;

import junit.framework.TestCase;
import org.fidonet.jftn.engine.script.ScriptManager;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.tests.tools.ConsoleOutputStream;
import org.junit.Test;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 5:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestCommand extends CommandInterpreter {

    @Test
    public void testCommandRegister() throws Exception {
        ScriptManager scriptManager = ScriptManager.getInstance();
        InputStream inputStream = ScriptManager.class.getClassLoader().getResourceAsStream("testCommand.py");
        try {
            scriptManager.runScript(inputStream);
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        Command command = CommandCollection.getInstance().findCommandByName("test");
        TestCase.assertNotNull(command);

        PrintStream console = System.out;
        ConsoleOutputStream consoleMonitor = new ConsoleOutputStream();
        System.setOut(new PrintStream(consoleMonitor, true));
        command.execute(new String[] {"test", "test2"});
        System.out.flush();
        System.setOut(console);
        TestCase.assertEquals("Test [test, test2]", consoleMonitor.getBuffer());

    }

}
