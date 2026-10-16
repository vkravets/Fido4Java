package org.fidonet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ConfigurationFragmentsTest {
    @TempDir
    Path root;

    private Path mainConfig() throws Exception {
        return Files.writeString(root.resolve("jftn.conf"),
                                 "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\nHidden=base\n");
    }

    private void fragment(String name, String contents) throws Exception {
        Files.createDirectories(root.resolve("conf.d"));
        Files.writeString(root.resolve("conf.d").resolve(name), contents);
    }

    @Test
    void loadedFileOnlyOverridesInFilenameOrderAndIgnoredExamples() throws Exception {
        Path main = mainConfig();
        fragment("10-first.conf", "Hidden=first\nCustomList=one\nCustomList=two\n");
        fragment("20-last.conf", "Hidden=last\nReceivedArchivePath=custom-archive\n");
        fragment("ignored.conf.example", "not valid configuration");
        JFtnConfig config = new JFtnConfig();
        config.load(main.toString());
        assertEquals("last", config.getValue("Hidden"));
        assertEquals(2, config.getValuesAsList("CustomList").size());
        assertEquals(Path.of("custom-archive"), config.getReceivedArchivePath());
        assertTrue(config.isPreserveReceivedFiles());
    }

    @Test
    void rejectedUiManagedOptionsAndMalformedFragments() throws Exception {
        Path main = mainConfig();
        fragment("manual.conf", "PreserveReceivedFiles=false\n");
        assertThrows(ParseConfigException.class, () -> new JFtnConfig().load(main.toString()));
        fragment("manual.conf", "invalid option\n");
        assertThrows(ParseConfigException.class, () -> new JFtnConfig().load(main.toString()));
    }
}
