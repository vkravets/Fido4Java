package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.jftn.plugins.ServiceContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ServiceConfigurationTest {
    @TempDir
    Path temporary;

    @Test
    void readOrderedFragmentsWithoutInventingStationOrStorageConfiguration() throws Exception {
        Path fragments = temporary.resolve("conf.d");
        Files.createDirectories(fragments);
        Files.writeString(fragments.resolve("90-rpc.conf"), "RpcEnabled=true\n");
        Files.writeString(fragments.resolve("99-local.conf"),
                          "RpcEnabled=false\nWebUiEnabled=false\n");
        ServiceContext context = new ServiceContext(temporary.resolve("jftn.conf"));
        JFtnConfig config = ServiceConfiguration.read(context);
        assertFalse(ServiceConfiguration.configured(context));
        assertFalse(config.isValidate());
        assertEquals("false", config.getValue("RpcEnabled"));
        assertEquals("false", config.getValue("WebUiEnabled"));
        assertEquals(0, config.getConfiguredLinks().size());
        assertFalse(Files.exists(context.configuration()));
    }

    @Test
    void rejectedUiManagedFieldsInBootstrapFragments() throws Exception {
        Files.createDirectories(temporary.resolve("conf.d"));
        Files.writeString(temporary.resolve("conf.d/95-web-ui.conf"), "SysOp=Forbidden\n");
        assertThrows(ParseConfigException.class,
                     () -> ServiceConfiguration.read(new ServiceContext(temporary.resolve("jftn.conf"))));
    }
}
