package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.ServiceContext;
import org.fidonet.mail.files.FileMailStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ServiceLoader;

class RpcPluginTest {
    @TempDir
    Path temporary;

    private JFtnConfig config(String options) throws Exception {
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(("SysOp=Test\nRuntimeProfile=transit\n"
                + "Link=1:500/101,1:500/100,-\nTmp=" + temporary.resolve("tmp") + "\nInbound="
                + temporary.resolve("in") + "\nOutbound=" + temporary.resolve("out") + "\nEchoPath="
                + temporary.resolve("echo") + "\n" + options).getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    @Test
    void discoveredRpcWithoutBrowserImplementation() {
        assertTrue(ServiceLoader.load(Plugin.class)
                                .stream()
                                .anyMatch(provider -> provider.type().equals(RpcPlugin.class)));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.fidonet.web.StaticUi"));
    }

    @Test
    void respectedDisabledAndInvalidConfigurationAndAllowedPreConfigurationDiscovery()
            throws Exception {
        RpcPlugin plugin = new RpcPlugin();
        assertTrue(plugin.enabled(new ServiceContext(temporary.resolve("missing.conf"))));
        assertFalse(plugin.enabled(new ServiceContext(temporary.resolve("config")).provide(JFtnConfig.class,
                                                                                           config("RpcEnabled=false\n"))));
        assertThrows(IllegalArgumentException.class,
                     () -> plugin.enabled(new ServiceContext(temporary.resolve("config")).provide(JFtnConfig.class,
                                                                                                  config("RpcEnabled=invalid\n"))));
    }

    @Test
    void RejectedSqlFreeStorageWithAnActionableCapabilityError() throws Exception {
        JFtnConfig config = config("RpcEnabled=true\n");
        try (FileMailStore storage = new FileMailStore(config)) {
            ServiceContext context = new ServiceContext(temporary.resolve("config")).provide(JFtnConfig.class,
                                                                                             config)
                                                                                    .provide(MailStorage.class,
                                                                                             storage);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                            () -> new RpcPlugin().start(context));
            assertTrue(failure.getMessage().contains("SQL mail storage"));
        }
    }
}
