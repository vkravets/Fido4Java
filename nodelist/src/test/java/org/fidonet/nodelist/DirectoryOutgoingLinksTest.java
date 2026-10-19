package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.OutgoingLinks;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class DirectoryOutgoingLinksTest {
    @TempDir
    Path root;

    private JFtnConfig config(String options) throws Exception {
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nNodeDirectoryPath="
                      + root.resolve("db/catalog")
                      + "\nLink=1:500/101,1:500/100,secret,localhost\n"
                      + options;
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private void directory() throws Exception {
        Path source = root.resolve("nodes.lst");
        Files.writeString(source,
                          ",103,Remote,Test,Sysop,-Unpublished-,300,INA:127.0.0.1,IBN:24555\r\n"
                          + "Down,104,Down,Test,Sysop,-Unpublished-,300,INA:127.0.0.1,IBN\r\n"
                          + ",105,Pstn,Test,Sysop,555-0100,9600,CM\r\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("db/catalog"))) {
            catalog.importSource(new DirectorySource("nodes",
                                                     DirectorySource.Format.NODELIST,
                                                     source,
                                                     1,
                                                     500,
                                                     StandardCharsets.US_ASCII,
                                                     false));
        }
    }

    @Test
    void resolvesOnlyWhenExplicitlyEnabledAndNeverCreatesIncomingTrust() throws Exception {
        directory();
        FTNAddr address = new FTNAddr("1:500/103");
        assertNull(OutgoingLinks.find(config(""), address));
        JFtnConfig config = config("AllowVirtualOutbound=true\n");
        Link link = OutgoingLinks.find(config, address);
        assertEquals(address, link.getAddr());
        assertEquals("127.0.0.1", link.getHostAddress());
        assertEquals(24555, link.getPort());
        assertEquals("", link.getPass());
        assertNull(config.getLink(address));
        assertEquals(1, config.getConfiguredLinks().size());
        assertNull(OutgoingLinks.find(config, new FTNAddr("1:500/104")));
        assertNull(OutgoingLinks.find(config, new FTNAddr("1:500/999")));
        assertNull(OutgoingLinks.find(config, new FTNAddr("1:500/105")));
        assertEquals(new FTNAddr("1:500/105"),
                     OutgoingLinks.find(config("AllowVirtualOutbound=true\nBsoEnabled=true\n"),
                                        new FTNAddr("1:500/105"))
                                  .getAddr());
    }
}
