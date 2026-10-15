package org.fidonet.tests.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.jftn.engine.script.AbstractScriptManager;
import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.fidonet.jftn.engine.script.exception.EngineNotFoundException;
import org.fidonet.jftn.engine.script.exception.NotSupportedScriptEngine;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.SimpleBindings;

public class ScriptEngineFailureTest {
    private static class ReplaceableGroovyManager extends GroovyScriptManager {
        ReplaceableGroovyManager() throws EngineNotFoundException {
            super();
        }

        void install(ScriptEngine engine) {
            setScriptEngine(engine);
        }
    }

    @Test
    public void missingEngineReportsRequestedExtension() {
        try {
            new AbstractScriptManager() {
                @Override
                protected String getFileExtension() {
                    return "jftn-no-such-engine";
                }
            };
            fail("Missing engine accepted");
        } catch (EngineNotFoundException expected) {
            assertTrue(expected.getMessage().contains("jftn-no-such-engine"));
        }
    }

    @Test
    public void nullEngineIsRejectedWithoutDiscardingWorkingEngine() throws Exception {
        ReplaceableGroovyManager manager = new ReplaceableGroovyManager();
        ScriptEngine original = manager.getEngine();
        try {
            manager.install(null);
            fail("Null engine accepted");
        } catch (VerifyError expected) {
            assertSame(original, manager.getEngine());
        }
    }

    @Test
    public void engineWithoutInvocableProducesExplicitErrorAndCanRecover() throws Exception {
        ReplaceableGroovyManager manager = new ReplaceableGroovyManager();
        ScriptEngineFactory factory = manager.getEngine().getFactory();
        manager.install(new AbstractScriptEngine() {
            @Override
            public Object eval(String script, ScriptContext context) {
                return null;
            }

            @Override
            public Object eval(Reader script, ScriptContext context) {
                return null;
            }

            @Override
            public Bindings createBindings() {
                return new SimpleBindings();
            }

            @Override
            public ScriptEngineFactory getFactory() {
                return factory;
            }
        });
        try {
            manager.registerScript("unsupported", "script");
            fail("Non-invocable engine accepted");
        } catch (NotSupportedScriptEngine expected) {
            assertTrue(expected.getCause() instanceof ClassCastException);
        }
        List<String> calls = new ArrayList<>();
        manager.registerScript("working", "def register(api) { api.add('working') }", calls);
        manager.unregisterScript("working", calls);
        assertEquals(Collections.singletonList("working"), calls);
    }
}
