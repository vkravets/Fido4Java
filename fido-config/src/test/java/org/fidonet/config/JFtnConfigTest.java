package org.fidonet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

public class JFtnConfigTest {
    @TempDir
    public File temporary;

    private String validConfig() {
        return "Inbound=incoming\nTmp="
               + new File(temporary, "tmp")
               + "\nEchoPath="
               + new File(temporary, "echo")
               + "\n"
               + "Link=1:500/101,1:500/100,secret,localhost\n"
               + "SysOp=Test User\n"
               + "Outbound=outgoing\n";
    }

    private JFtnConfig load(String text) throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    @Test
    public void loadsMultipleLinksAndDefaultOptions() throws Exception {
        JFtnConfig config = load(validConfig() + "Link=1:500/102,1:500/100,second,example.test\n");
        assertTrue(config.isValidate());
        assertEquals("secret", config.getLink(new FTNAddr("1:500/101")).getPass());
        assertEquals("second", config.getLink(new FTNAddr("1:500/102")).getPass());
        assertNull(config.getLink(new FTNAddr("1:500/999")));
        assertEquals("incoming", config.getInbound());
        assertEquals("outgoing", config.getOutbound());
        assertEquals(Integer.valueOf(1), config.isDeleteTossedFiles());
        assertEquals(Integer.valueOf(0),
                     load(validConfig() + "DeleteTossed=0\n").isDeleteTossedFiles());
    }

    @Test
    public void rejectsEachMissingRequiredOption() throws Exception {
        for (String key : Arrays.asList("Inbound",
                                        "Tmp",
                                        "EchoPath",
                                        "Link",
                                        "SysOp",
                                        "Outbound")) {
            String text = validConfig().replaceAll("(?m)^" + key + "=.*\\n", "");
            try {
                load(text);
                fail("Missing " + key + " must invalidate config");
            } catch (ParseConfigException expected) {
                assertEquals("Config is not valid!", expected.getMessage());
            }
        }
    }

    @Test
    public void createsDirectoriesAndReturnsStableTemporaryPath() throws Exception {
        JFtnConfig config = load(validConfig());
        String first = config.getTmpDir();
        assertTrue(new File(first).isDirectory());
        assertEquals(first, config.getTmpDir());
        String echo = config.getEchoPath();
        assertTrue(new File(echo).isDirectory());
        assertEquals(echo, config.getEchoPath());
    }

    @Test
    public void createsNestedAreaListAndKeepsExistingContents() throws Exception {
        Path path = temporary.toPath().resolve("nested/areas");
        JFtnConfig config = load(validConfig() + "AreaListFile=" + path + "\n");
        assertEquals(path.toString(), config.getArealistFile());
        assertTrue(Files.isRegularFile(path));
        Files.writeString(path, "echoarea TEST\n");
        config.getArealistFile();
        assertEquals("echoarea TEST\n", Files.readString(path));
    }

    @Test
    public void createsAreaListWithoutParentDirectoryComponent() throws Exception {
        Path path = Path.of("jftn-test-areas-" + UUID.randomUUID());
        try {
            JFtnConfig config = load(validConfig() + "AreaListFile=" + path + "\n");
            assertEquals(path.toString(), config.getArealistFile());
            assertTrue(Files.isRegularFile(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void reportsAreaListDirectoryBlockedByAFile() throws Exception {
        File parent = Files.createFile(temporary.toPath().resolve("blocked")).toFile();
        JFtnConfig config = load(validConfig()
                                 + "AreaListFile="
                                 + new File(parent, "areas")
                                 + "\n");
        try {
            config.getArealistFile();
            fail("A file cannot serve as the parent directory");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("area-list directory"));
        }
        assertTrue(parent.isFile());
    }
}
