package org.fidonet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class RuntimeProfileTest {
    private JFtnConfig config(String options) throws Exception {
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(("SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                + options).getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    @Test
    void preservedFullDefaultAndRejectedUnknownProfiles() throws Exception {
        assertEquals(RuntimeProfile.FULL, RuntimeProfile.read(config("")));
        assertThrows(IllegalArgumentException.class,
                     () -> RuntimeProfile.read(config("RuntimeProfile=typo\n")));
    }

    @Test
    void rejectedUnavailableServicesBeforeStarting() throws Exception {
        for (String key : new String[]{"EchoSubscriptions=database", "NetmailRoutes=database",
                "RobotAccessSource=database", "FileEchoSource=database",
                "NodeDirectoryEnabled=true", "JamBridgeEnabled=true",
                "AllowVirtualOutbound=true"}) {
            JFtnConfig candidate = config("RuntimeProfile=legacy\n" + key + "\n");
            assertThrows(IllegalArgumentException.class,
                         () -> RuntimeProfile.read(candidate).validate(candidate));
        }
        RuntimeProfile.HEADLESS.validate(config(""));
        RuntimeProfile.TRANSIT.validate(config(""));
    }
}
