package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.fidonet.jftn.engine.script.ScriptApi;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandRegistry;
import org.fidonet.jftn.share.ScriptHookRegistry;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class TossScriptTest {
    @Test
    public void compilesAndRegistersTheBundledTossCommand() throws Exception {
        CommandCollection commands = new CommandCollection();
        ScriptApi service = new ScriptApi(null,
                                          new ScriptHookRegistry(),
                                          new CommandRegistry(commands));
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
        assertNotNull(engine);
        engine.put("jftn", service);
        engine.eval(Files.readString(Path.of("src/main/static-content/scripts/toss.groovy")));
        assertEquals("TossCommand",
                     commands.findCommandByName("tosspack").getClass().getSimpleName());
    }
}
