package org.fidonet.tests.share;

import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;

public class ScriptReloadRegressionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private String script(String version) {
        return "def state = null\n"
                + "def register(api) { state = api; api.add('register " + version + "') }\n"
                + "def unload(api) { assert state.is(api); api.add('unload " + version + "') }\n";
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
        assertEquals(Arrays.asList("register one", "unload one", "register two", "unload two", "register two"), calls);
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
        assertEquals(Arrays.asList("register first", "register second", "register first",
                "unload first", "unload second", "unload first"), calls);
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
