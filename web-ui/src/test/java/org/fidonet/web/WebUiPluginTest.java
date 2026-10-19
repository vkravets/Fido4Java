package org.fidonet.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.plugins.ServiceContext;
import org.fidonet.jftn.plugins.ServicePlugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class WebUiPluginTest {
    @TempDir
    Path temporary;

    private ServiceContext context(String options) throws Exception {
        JFtnConfig config = new JFtnConfig();
        String text = "SysOp=Test\nLink=1:500/101,1:500/100,-\n"
                      + "Inbound="
                      + temporary.resolve("in")
                      + "\nOutbound="
                      + temporary.resolve("out")
                      + "\nEchoPath="
                      + temporary.resolve("echo")
                      + "\nTmp="
                      + temporary.resolve("tmp")
                      + "\n"
                      + options;
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(temporary.resolve("jftn.conf"), text);
        return new ServiceContext(temporary.resolve("jftn.conf")).provide(JFtnConfig.class, config);
    }

    @Test
    void skippedBootstrapWhenUiWasDisabledAndPreservedConfiguredFiles() throws Exception {
        ServiceContext disabled = context("RpcEnabled=true\nWebUiEnabled=false\n");
        new ServicePlugins(List.of(new WebUiPlugin())).prepare(disabled);
        assertFalse(Files.exists(temporary.resolve("database")));
        ServiceContext configured = context("RpcEnabled=true\nRpcHttpEnabled=true\n"
                                            + "RpcUser=admin\nRpcPassword=test-only-password\n");
        Path fragment = temporary.resolve("conf.d/operator.conf");
        Files.createDirectories(fragment.getParent());
        Files.writeString(fragment, "# Keep this file\n");
        String original = Files.readString(configured.configuration());
        new WebUiPlugin().prepare(configured);
        assertTrue(original.equals(Files.readString(configured.configuration())));
        assertTrue(Files.readString(fragment).equals("# Keep this file\n"));
        assertFalse(Files.exists(temporary.resolve("mail")));
    }

    @Test
    void disabledBrowserRoutesWithoutDisablingRpcCapability() throws Exception {
        ServiceContext context = context("RpcEnabled=true\nWebUiEnabled=false\n");
        assertFalse(new WebUiPlugin().enabled(context));
        assertTrue(new StaticUi().routes(context.require(JFtnConfig.class)).isEmpty());
    }

    @Test
    void requiredRpcAndHttpAndSkippedWhenRpcWasDisabled() throws Exception {
        WebUiPlugin plugin = new WebUiPlugin();
        ServiceContext context = context("RpcEnabled=true\nWebUiEnabled=true\n");
        assertThrows(IllegalArgumentException.class,
                     () -> new ServicePlugins(List.of(plugin)).prepare(context));
        assertThrows(IllegalArgumentException.class, () -> plugin.start(context));
        assertFalse(plugin.enabled(context("RpcEnabled=false\n")));
        assertTrue(plugin.enabled(new ServiceContext(temporary.resolve("missing.conf"))));
    }
}
