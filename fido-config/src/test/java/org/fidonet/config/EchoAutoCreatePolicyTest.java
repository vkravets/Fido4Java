package org.fidonet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class EchoAutoCreatePolicyTest {
    private JFtnConfig config(String options) throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(("SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n"
                + options).getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    @Test
    void requiredExactConfiguredSendersDatabaseSubscriptionsAndReceivingRights() throws Exception {
        assertEquals(Map.of(), EchoAutoCreatePolicy.read(config("")));
        assertEquals(Map.of("1:500/101.0", "WRITE"),
                     EchoAutoCreatePolicy.read(config("EchoSubscriptions=database\nAutoCreateEchoFrom=1:500/101,write\n")));
        for (String entry : new String[]{"1:500/999,BOTH", "1:500/101,READ", "*,BOTH", "1:500/101",
                "1:500/101,BOTH,extra", "1:500/101,BOTH\nAutoCreateEchoFrom=1:500/101.0,WRITE"}) {
            JFtnConfig invalid = config("EchoSubscriptions=database\nAutoCreateEchoFrom="
                                        + entry
                                        + "\n");
            assertThrows(IllegalArgumentException.class, () -> EchoAutoCreatePolicy.read(invalid));
        }
        JFtnConfig fileMode = config("AutoCreateEchoFrom=1:500/101,BOTH\n");
        assertThrows(IllegalArgumentException.class, () -> EchoAutoCreatePolicy.read(fileMode));
    }
}
