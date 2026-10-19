package org.fidonet.jftn.tosser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

class BsoAttachmentsTest {
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
                      + "\nFileAttachRoot="
                      + root.resolve("attachments")
                      + "\nLink=1:500/101,1:500/100,secret,localhost\n";
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        Files.createDirectories(root.resolve("attachments"));
        return config;
    }

    private Message message(String subject) {
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("1:500/100"),
                                      new FTNAddr("1:500/101"),
                                      subject,
                                      "Body\r",
                                      new Date());
        message.setAttributes(17);
        return message;
    }

    @Test
    void acceptsLocalFilesButNeverEscapesTheAttachmentRoot() throws Exception {
        JFtnConfig config = config();
        Files.writeString(root.resolve("attachments/ONE.ZIP"), "payload");
        Files.writeString(root.resolve("SECRET.ZIP"), "secret");
        assertEquals(1, BsoAttachments.sources(config, message("ONE.ZIP")).size());
        assertThrows(IOException.class,
                     () -> BsoAttachments.sources(config, message("../SECRET.ZIP")));
    }

    @Test
    void rejectsMistypedRetentionPolicyAndLegacyTransitWithoutASource() throws Exception {
        JFtnConfig config = config();
        Files.writeString(root.resolve("attachments/ONE.ZIP"), "payload");
        Message transit = message("ONE.ZIP");
        transit.setAttributes(49);
        assertThrows(IOException.class, () -> BsoAttachments.sources(config, transit));
        config.load(new ByteArrayInputStream("BsoKeepTransitFiles=tru\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                     () -> BsoAttachments.sources(config, message("ONE.ZIP")));
        assertEquals("payload", Files.readString(root.resolve("attachments/ONE.ZIP")));
    }

    @Test
    void preservesTransitPeerAcrossSqlRoundTripAndNeverFallsBackToLocalFiles() throws Exception {
        JFtnConfig config = config();
        Files.writeString(root.resolve("attachments/ONE.ZIP"), "local secret");
        Message message = message("ONE.ZIP");
        message.setUpLink(new FTNAddr("1:500/102"));
        String url = "jdbc:h2:" + root.resolve("mail").toAbsolutePath();
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager(url));
        base.open();
        try {
            base.saveNetmail(message, "transit", "1:500/101");
        } finally {
            base.close();
        }
        base = new MailDatabaseFacade(new OrmManager(url));
        base.open();
        try {
            Message restored = base.getQueuedNetmail().get(0).getMessage();
            assertEquals(message.getUpLink(), restored.getUpLink());
            assertThrows(IOException.class, () -> BsoAttachments.sources(config, restored));
            base.visitQueuedNetmail(queued -> assertEquals(message.getUpLink(),
                                                           queued.getMessage().getUpLink()));
        } finally {
            base.close();
        }
    }
}
