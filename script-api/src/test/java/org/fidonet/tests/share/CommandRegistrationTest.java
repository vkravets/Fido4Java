package org.fidonet.tests.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.fidonet.jftn.engine.script.ScriptApi;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandRegistry;
import org.fidonet.jftn.share.ScriptHookRegistry;

import org.junit.jupiter.api.Test;

public class CommandRegistrationTest {
    @Test
    public void registersCallableCommandsAndPreservesFirstRegistration() throws Exception {
        CommandCollection commands = new CommandCollection();
        ScriptApi service = new ScriptApi(null,
                                          new ScriptHookRegistry(),
                                          new CommandRegistry(commands));
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
