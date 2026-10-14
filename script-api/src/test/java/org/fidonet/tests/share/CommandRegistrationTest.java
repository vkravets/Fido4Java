package org.fidonet.tests.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.fidonet.jftn.engine.script.JFtnScriptService;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;

import org.junit.Test;

public class CommandRegistrationTest {
    @Test
    public void registersCallableCommandsAndPreservesFirstRegistration() throws Exception {
        CommandCollection commands = new CommandCollection();
        JFtnScriptService service = new JFtnScriptService(null,
                                                          new HookInterpreter(),
                                                          new CommandInterpreter(commands));
        Command<String, String> command = text -> "reply " + text;
        service.registerCommand("test", command);
        service.registerCommand("test", (Command<String, String>) text -> "replacement");
        service.registerCommand(null, command);
        service.registerCommand("", command);
        service.registerCommand("missing", null);
        assertSame(command, commands.findCommandByName("test"));
        assertEquals("reply hello", commands.findCommandByName("test").execute("hello"));
        assertEquals(1, commands.getCommands().size());
        assertNull(commands.findCommandByName("missing"));
    }
}
