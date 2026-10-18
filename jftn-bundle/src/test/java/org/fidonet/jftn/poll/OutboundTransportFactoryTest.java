package org.fidonet.jftn.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.protocol.ProcessConnector;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class OutboundTransportFactoryTest {
    @TempDir
    Path root;

    @Test
    void selectsSshOnlyForMappedLinksAndKeepsArgumentsAndRemoteQuotingIntact() throws Exception {
        Path sshConfig = root.resolve("ssh config");
        Files.writeString(sshConfig, "Host uplink\n");
        JFtnConfig config = config("BinkpSsh=1:500/101|uplink|/mail with 'quotes'/binkd.conf\n"
                                   + "BinkpSshConfig="
                                   + sshConfig
                                   + "\n");
        OutboundTransportFactory factory = new OutboundTransportFactory(config);
        factory.validate();
        assertTrue(factory.create(config.getLink(new FTNAddr("1:500/101"))) instanceof ProcessConnector);
        assertTrue(factory.create(config.getLink(new FTNAddr("1:500/102"))) instanceof Client);
        List<String> command = factory.command(new String[]{"1:500/101", "uplink",
                "/mail with 'quotes'/binkd.conf"});
        assertEquals(sshConfig.toString(), command.get(command.indexOf("-F") + 1));
        assertTrue(command.contains("BatchMode=yes"));
        assertTrue(command.contains("StrictHostKeyChecking=yes"));
        assertTrue(command.contains("-T"));
        assertEquals("exec 'binkd' -i -q '/mail with '\"'\"'quotes'\"'\"'/binkd.conf'",
                     command.get(command.size() - 1));
        assertFalse(String.join(" ", command).contains("binkp-password"));
    }

    @Test
    void absentSshConfigurationKeepsDefaultTcpConnections() throws Exception {
        JFtnConfig config = config("");
        OutboundTransportFactory factory = new OutboundTransportFactory(config);
        factory.validate();
        assertTrue(factory.create(config.getLink(new FTNAddr("1:500/101"))) instanceof Client);
    }

    @Test
    void rejectsUnknownPeersDuplicateMappingsAndSshOptionInjection() {
        for (String mapping : new String[]{"1:500/999|uplink|/etc/binkd.conf",
                "1:500/101|-oProxyCommand=bad|/etc/binkd.conf", "1:500/101||/etc/binkd.conf",
                "1:500/101|uplink", "1:500/101|uplink|-m",
                "1:500/101|uplink|/etc/binkd.conf|binkd|extra",
                "1:500/101|uplink|/etc/binkd.conf\nBinkpSsh=1:500/101|other|/etc/binkd.conf"}) {
            assertThrows(IllegalArgumentException.class,
                         () -> new OutboundTransportFactory(config("BinkpSsh="
                                                                   + mapping
                                                                   + "\n")).validate());
        }
    }

    @Test
    void rejectsMissingExplicitSshConfigBeforeOpeningAConnection() throws Exception {
        OutboundTransportFactory factory = new OutboundTransportFactory(config("BinkpSsh=1:500/101|uplink|/etc/binkd.conf\nBinkpSshConfig="
                + root.resolve("missing") + "\n"));
        assertThrows(IllegalArgumentException.class, factory::validate);
    }

    private JFtnConfig config(String extra) throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(("SysOp=Test\nInbound=in\nOutbound=out\nTmp=" + root
                + "\nEchoPath=echo\nLink=1:500/101,1:500/100,binkp-password,localhost\n"
                + "Link=1:500/102,1:500/100,binkp-password,localhost\n" + extra)
                                                                                .getBytes(StandardCharsets.UTF_8)));
        return config;
    }
}
