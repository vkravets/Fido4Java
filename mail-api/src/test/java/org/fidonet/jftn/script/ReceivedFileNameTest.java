package org.fidonet.jftn.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.config.JFtnConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class ReceivedFileNameTest {
    @TempDir
    Path root;

    @Test
    void acceptsSpacesDosPunctuationAndUnicodeButRejectsUnsafePortableNames() {
        for (String name : new String[]{"abc def.zip", "TOOLS$.ZIP", "mail!@#%.pkt", "лист.txt",
                ".hidden", "a".repeat(255)}) {
            assertEquals(name, ReceivedFile.safeName(name));
        }
        for (String name : new String[]{"", ".", "..", "../escape", "..\\escape", "C:mail.pkt",
                "mail\0.pkt", "mail\r.pkt", "mail\n.pkt", "file?", "file*", "file|", "file\"",
                "file<", "file>", "file.", "file ", "NUL", "con.pkt", "COM1.txt", "LPT9",
                "a".repeat(256), "ї".repeat(128)}) {
            assertThrows(IllegalArgumentException.class, () -> ReceivedFile.safeName(name), name);
        }
        assertThrows(IllegalArgumentException.class, () -> ReceivedFile.safeName(null));
    }

    @Test
    void recordsAndReloadsOriginalNamesWithSpacesAndMultibyteCharacters() throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(("SysOp=Test\nOutbound="
                + root.resolve("out") + "\nTmp=" + root.resolve("tmp") + "\nEchoPath="
                + root.resolve("echo") + "\nInbound=" + root
                + "\nLink=1:500/101,1:500/100,password,localhost\n").getBytes(StandardCharsets.UTF_8)));
        Path inbox = root.resolve("peers/1-500-101-0");
        Files.createDirectories(inbox);
        for (String name : new String[]{"abc def.zip", "TOOLS$.ZIP", "ї".repeat(100) + ".txt"}) {
            Path payload = inbox.resolve("payload");
            Files.writeString(payload, "content");
            ReceivedFile.record(payload, name);
            assertEquals(name, ReceivedFile.list(config).get(0).getName());
        }
    }
}
