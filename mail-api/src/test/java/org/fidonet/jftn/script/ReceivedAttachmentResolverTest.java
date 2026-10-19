package org.fidonet.jftn.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.BsoOutbound;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class ReceivedAttachmentResolverTest {
    @TempDir
    Path root;

    private JFtnConfig config() throws Exception {
        JFtnConfig config = new JFtnConfig();
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nBsoEnabled=true\nLink=1:500/101,1:500/100,secret,localhost"
                      + "\nLink=1:500/102,1:500/100,secret,localhost\n";
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private Path received(JFtnConfig config, String peer, String stored, String content)
            throws Exception {
        Path directory = Path.of(config.getInbound(),
                                 "peers",
                                 ReceivedFile.directoryName(new FTNAddr(peer)));
        Files.createDirectories(directory);
        Path payload = Files.writeString(directory.resolve(stored), content);
        ReceivedFile.record(payload, "ONE.ZIP");
        return payload;
    }

    @Test
    void usesOnlyTheAuthenticatedSourceAndPublishesTheOriginalBasename() throws Exception {
        JFtnConfig config = config();
        received(config, "1:500/102", "hash-other.ZIP", "other peer");
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("1:500/999"),
                                      new FTNAddr("1:500/102"),
                                      "ONE.ZIP",
                                      "Body\r",
                                      new Date());
        message.setUpLink(new FTNAddr("1:500/101"));
        message.setAttributes(17);
        Link target = config.getLink(new FTNAddr("1:500/102"));
        OutboundSpool spool = new OutboundSpool(config);
        assertThrows(IOException.class, () -> spool.enqueue("transit", target, message));
        received(config, "1:500/101", "hash-correct.ZIP", "correct peer");
        spool.enqueue("transit", target, message);
        List<String> lines = Files.readAllLines(new BsoOutbound(config).control(target.getAddr(),
                                                                                "flo"));
        Path copy = Path.of(lines.get(0).substring(1));
        assertEquals("ONE.ZIP", copy.getFileName().toString());
        assertEquals("correct peer", Files.readString(copy));
    }

    @Test
    void defersConflictingReceiptsWithTheSameOriginalName() throws Exception {
        JFtnConfig config = config();
        received(config, "1:500/101", "first.ZIP", "first");
        received(config, "1:500/101", "second.ZIP", "second");
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("1:500/999"),
                                      new FTNAddr("1:500/102"),
                                      "ONE.ZIP",
                                      "Body\r",
                                      new Date());
        message.setUpLink(new FTNAddr("1:500/101"));
        message.setAttributes(17);
        assertThrows(IOException.class,
                     () -> new OutboundSpool(config).enqueue("ambiguous",
                                                             config.getLink(new FTNAddr("1:500/102")),
                                                             message));
    }
}
