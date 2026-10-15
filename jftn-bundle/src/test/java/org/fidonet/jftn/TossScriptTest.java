package org.fidonet.jftn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.fidonet.jftn.engine.script.JFtnScriptService;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class TossScriptTest {
    @Test
    public void compilesAndRegistersTheBundledTossCommand() throws Exception {
        CommandCollection commands = new CommandCollection();
        JFtnScriptService service = new JFtnScriptService(null,
                                                          new HookInterpreter(),
                                                          new CommandInterpreter(commands));
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
        assertNotNull(engine);
        engine.put("jftn", service);
        engine.eval(Files.readString(Path.of("src/main/static-content/scripts/toss.groovy")));
        assertEquals("TossCommand",
                     commands.findCommandByName("tosspack").getClass().getSimpleName());
    }
}
