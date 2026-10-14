package org.fidonet.tests.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import org.fidonet.jftn.engine.script.GroovyScriptManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class ScriptReloadRegressionTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void emptyMissingAndSyntacticallyInvalidScriptsDoNotBlockValidScripts()
            throws Exception {
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        manager.reloadScripts(Collections.emptyMap(), calls);
        manager.unregisterScript("missing", calls);
        manager.registerScript("empty", "", calls);
        ScriptEngine emptyEngine = manager.getEngine();
        manager.unregisterScript("empty", calls);
        assertNotSame(emptyEngine, manager.getEngine());
        Path invalid = temporary.newFile("invalid.groovy").toPath();
        Files.writeString(invalid, "def register( {");
        manager.reloadScripts(Collections.singletonMap(invalid.toString(), ""), calls);
        manager.reloadScripts(Collections.singletonMap(invalid + ".missing.groovy", ""), calls);
        manager.registerScript("valid", script("valid"), calls);
        manager.unregisterScript("valid", calls);
        assertEquals(Arrays.asList("register valid", "unload valid"), calls);
    }

    @Test
    public void failedUnloadCanBeRetriedWithoutDiscardingRegisteredState() throws Exception {
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        manager.registerScript("retry", scriptWithRetryableUnload(), calls);
        try {
            manager.unregisterScript("retry", calls);
            fail("Unload failure ignored");
        } catch (ScriptException expected) {
        }
        manager.unregisterScript("retry", calls);
        manager.unregisterScript("retry", calls);
        assertEquals(Arrays.asList("registered", "failed", "unloaded"), calls);
    }

    private String script(String version) {
        return "def state = null\n"
               + "def register(api) {\n"
               + "    state = api\n"
               + "    api.add('register "
               + version
               + "')\n"
               + "}\n"
               + "def unload(api) {\n"
               + "    assert state.is(api)\n"
               + "    api.add('unload "
               + version
               + "')\n"
               + "}\n";
    }

    private String scriptWithRetryableUnload() {
        return "def register(api) {\n"
               + "    api.add('registered')\n"
               + "}\n"
               + "def unload(api) {\n"
               + "    if (api.size() == 1) {\n"
               + "        api.add('failed')\n"
               + "        throw new IllegalStateException('retry')\n"
               + "    }\n"
               + "    api.add('unloaded')\n"
               + "}\n";
    }

    @Test
    public void loadedCallbacksSurviveGarbageCollection() throws Exception {
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        manager.registerScript("first", script("first"), calls);
        manager.registerScript("second", script("second"), calls);
        // Registered hooks must remain callable for the whole script lifetime.
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(20);
        }
        manager.unregisterScript("first", calls);
        manager.unregisterScript("second", calls);
        assertEquals(Arrays.asList("register first",
                                   "register second",
                                   "unload first",
                                   "unload second"),
                     calls);
    }

    @Test
    public void unloadedCallbacksAreNotReusedByTheNextScript() throws Exception {
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        manager.registerScript("old", script("old"), calls);
        manager.unregisterScript("old", calls);
        manager.registerScript("new", "def register(api) { api.add('register new') }", calls);
        manager.unregisterScript("new", calls);
        assertEquals(Arrays.asList("register old", "unload old", "register new"), calls);
    }

    @Test
    public void failedRegistrationDoesNotLeaveCallbacksInTheNextScript() throws Exception {
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        try {
            manager.registerScript("broken",
                                   "def register(api) { throw new IllegalStateException('failed') }\n"
                                           + "def unload(api) { api.add('obsolete unload') }",
                                   calls);
            fail("Expected registration failure");
        } catch (ScriptException expected) {
        }
        manager.registerScript("working",
                               "def register(api) { api.add('register working') }",
                               calls);
        manager.unregisterScript("working", calls);
        assertEquals(Collections.singletonList("register working"), calls);
    }

    @Test
    public void loadsGroovyFilesPreservesParamsAndReloadsOnlyWhenNeeded() throws Exception {
        Path file = temporary.newFile("script.groovy").toPath();
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        Map<String, String> files = Collections.singletonMap(file.toString(), "");
        Files.writeString(file, script("one"));
        manager.reloadScripts(files, calls);
        manager.reloadScripts(files, calls);
        assertEquals(Arrays.asList("register one"), calls);
        Files.writeString(file, script("two"));
        manager.reloadScripts(files, calls);
        assertEquals(Arrays.asList("register one", "unload one", "register two"), calls);
        manager.reloadScripts(files, Boolean.TRUE, calls);
        assertEquals(Arrays.asList("register one",
                                   "unload one",
                                   "register two",
                                   "unload two",
                                   "register two"),
                     calls);
        manager.unregisterScript(file.toString(), calls);
        assertEquals("unload two", calls.get(calls.size() - 1));
    }

    @Test
    public void cachesAndScriptStateAreIsolatedByManagerAndFile() throws Exception {
        Path first = temporary.newFile("first.groovy").toPath();
        Path second = temporary.newFile("second.groovy").toPath();
        Files.writeString(first, script("first"));
        Files.writeString(second, script("second"));
        GroovyScriptManager manager = new GroovyScriptManager();
        GroovyScriptManager other = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        manager.reloadScripts(Collections.singletonMap(first.toString(), ""), calls);
        manager.reloadScripts(Collections.singletonMap(second.toString(), ""), calls);
        other.reloadScripts(Collections.singletonMap(first.toString(), ""), calls);
        manager.unregisterScript(first.toString(), calls);
        manager.unregisterScript(second.toString(), calls);
        other.unregisterScript(first.toString(), calls);
        assertEquals(Arrays.asList("register first",
                                   "register second",
                                   "register first",
                                   "unload first",
                                   "unload second",
                                   "unload first"),
                     calls);
    }

    @Test
    public void failedRegistrationCanBeRetriedAndWrongExtensionIsIgnored() throws Exception {
        Path file = temporary.newFile("retry.groovy").toPath();
        GroovyScriptManager manager = new GroovyScriptManager();
        List<String> calls = new ArrayList<>();
        Map<String, String> files = Collections.singletonMap(file.toString(), "");
        Files.writeString(file, "def register(api) { throw new IllegalStateException('failed') }");
        manager.reloadScripts(files, calls);
        Files.writeString(file, script("fixed"));
        manager.reloadScripts(files, calls);
        manager.reloadScripts(Collections.singletonMap(file + ".bak", ""), calls);
        assertEquals(Arrays.asList("register fixed"), calls);
        manager.unregisterScript(file.toString(), calls);
    }
}
