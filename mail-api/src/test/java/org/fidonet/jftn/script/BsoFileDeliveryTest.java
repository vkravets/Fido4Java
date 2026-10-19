package org.fidonet.jftn.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.BsoOutbound;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class BsoFileDeliveryTest {
    @TempDir
    Path root;

    @Test
    void handsPayloadAndTicToBsoInOrderAndReconcilesOnlyAfterBothCopiesDisappear()
            throws Exception {
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nBsoEnabled=true\nBsoOutbound="
                      + root.resolve("bso")
                      + "\nLink=1:500/101,1:500/100,secret,localhost\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        Link peer = config.getConfiguredLinks().get(0);
        FileDeliveryQueue queue = new FileDeliveryQueue(config);
        Path source = Files.writeString(root.resolve("LIST.001"), "payload");
        queue.enqueue(peer, "delivery", source, "LIST.001", "Area TEST\r\nFile LIST.001\r\n");
        assertEquals(List.of(), queue.pending(peer));
        Path flo = new BsoOutbound(config).control(peer.getAddr(), "flo");
        List<String> entries = Files.readAllLines(flo);
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).endsWith("LIST.001"));
        assertTrue(entries.get(1).endsWith(".tic"));
        Files.delete(Path.of(entries.get(0).substring(1)));
        assertEquals(List.of(), queue.pending(peer));
        assertEquals(entries, Files.readAllLines(flo));
        Files.delete(Path.of(entries.get(1).substring(1)));
        Files.delete(flo);
        assertEquals(List.of(), new FileDeliveryQueue(config).pending(peer));
        queue.enqueue(peer, "delivery", source, "LIST.001", "Area TEST\r\nFile LIST.001\r\n");
        assertEquals(List.of(), queue.pending(peer));
        assertEquals("payload", Files.readString(source));
    }
}
