package org.fidonet.jftn.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ExternalMailScriptsTest {
    @TempDir
    Path root;

    private JFtnConfig configuration(Path... scripts) throws Exception {
        String directory = root.toString().replace('\\', '/');
        StringBuilder text = new StringBuilder("SysOp=Test\nRobotAccessSource=files\nTmp="
                + directory + "/tmp\nInbound=" + directory + "/in\nOutbound=" + directory
                + "/out\nEchoPath=" + directory
                + "/echo\nLink=1:500/101,1:500/100,secret,localhost\nMailScriptCompileThreads=4\n");
        for (Path script : scripts)
            text.append("MailScript=").append(script.toString().replace('\\', '/')).append("\n");
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private Path script(String name, String register) throws IOException {
        Path path = root.resolve(name + ".groovy");
        return Files.writeString(path,
                                 "def register(api) { " + register + " }; "
                                         + "def unload(api) { api.state.put('order', api.state.get('order') + '-"
                                         + name + "') }");
    }

    private MailAutomationRuntime open(JFtnConfig config) throws IOException {
        return new MailAutomationRuntime(config, null, null, List.of());
    }

    @Test
    void parallelPreparationPreservesRegistrationCallbacksDirectoriesAndReverseUnload()
            throws Exception {
        Path first = script("first",
                            "api.state.put('order', 'first'); api.onScan { api.state.put('scan', 'ok') }; "
                                    + "api.state.put('directory', api.scriptDirectory.toString())");
        Path second = script("second",
                             "api.state.put('order', api.state.get('order') + '+second')");
        JFtnConfig config = configuration(first, second);
        for (int restart = 0; restart < 2; restart++) {
            MailAutomationRuntime runtime = open(config);
            assertEquals("first+second", runtime.getState().get("order"));
            assertEquals(root.toString(), runtime.getState().get("directory"));
            runtime.beforeExport(null);
            assertEquals("ok", runtime.getState().get("scan"));
            runtime.close();
            assertEquals("first+second-second-first", runtime.getState().get("order"));
        }
    }

    @Test
    void compileFailureDoesNotRegisterAnyScriptAndRegistrationFailureUnloadsPredecessors()
            throws Exception {
        Path first = script("first", "api.state.put('order', 'first')");
        Path broken = Files.writeString(root.resolve("broken.groovy"), "def invalid( {");
        assertThrows(IOException.class, () -> open(configuration(first, broken)));
        ScriptState state = new ScriptState(root.resolve("tmp/scripts"));
        assertNull(state.get("order"));
        Files.writeString(broken,
                          "def register(api) { throw new IllegalArgumentException('bad config') }");
        assertThrows(IOException.class, () -> open(configuration(first, broken)));
        assertEquals("first-first", state.get("order"));
    }

    @Test
    void workerSettingsAreBoundedAndRejectInvalidValues() {
        assertEquals(4, ExternalMailScripts.compileThreads("10", 4));
        assertEquals(1, ExternalMailScripts.compileThreads("1", 20));
        int automatic = ExternalMailScripts.compileThreads("auto", 20);
        assertTrue(automatic >= 1 && automatic <= 4);
        assertThrows(IllegalArgumentException.class,
                     () -> ExternalMailScripts.compileThreads("0", 4));
        assertThrows(IllegalArgumentException.class,
                     () -> ExternalMailScripts.compileThreads("33", 4));
    }
}
