package org.fidonet.jftn.tosser.jam;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.db.objects.ArchiveReceipt;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class JamArchiveImporterTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Message message(boolean echo, boolean id) {
        return new Message("Old Sysop",
                           "Remote",
                           new FTNAddr("2:500/102"),
                           new FTNAddr("2:500/101"),
                           "Historical",
                           (echo ? "AREA:TEST\r" : "")
                           + (id ? "\1MSGID: 2:500/102 old\r" : "")
                           + "Historical text\r",
                           new Date(1700000000000L));
    }

    @Test
    public void importsHistoricalMailWithoutChangingSourceOrSchedulingDelivery() throws Exception {
        Path root = temporary.getRoot().toPath();
        JFtnConfig config = new JFtnConfig();
        String directory = root.toString().replace('\\', '/');
        config.load(new ByteArrayInputStream(("Inbound=" + directory + "/in\nTmp=" + directory
                + "/tmp\nOutbound=" + directory + "/out\nEchoPath=" + directory
                + "/echo\nSysOp=Test\nLink=2:500/101,2:500/102,secret,localhost\n").getBytes(StandardCharsets.UTF_8)));
        OrmManager orm = new OrmManager("jdbc:h2:mem:jamArchive" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            assertTrue(base.createArea("test"));
            base.configureLink(config.getLink(new FTNAddr("2:500/101")), "secret");
            base.configureSubscription(config.getLink(new FTNAddr("2:500/101")),
                                       "test",
                                       AccessLevel.BOTH);
            JamArea source = new JamArea(root.resolve("archive"), "test", new FTNAddr("2:500/102"));
            source.create();
            source.append(message(true, true), JamArea.ECHO | JamArea.LOCAL);
            source.append(message(true, false), JamArea.ECHO | JamArea.LOCAL);
            byte[] header = Files.readAllBytes(Path.of(source.getPath() + ".jhr"));
            byte[] index = Files.readAllBytes(Path.of(source.getPath() + ".jdx"));
            Files.delete(Path.of(source.getPath() + ".jlr"));
            assertEquals(2, JamArchiveImporter.importInto(source, base).getImported());
            assertEquals(2, JamArchiveImporter.importInto(source, base).getDuplicates());
            assertEquals(2, base.getMessageSize("test"));
            assertTrue(base.isArchived(message(true, true)));
            assertEquals(source.getPath().toString(),
                         orm.<ArchiveReceipt, String>getDao(ArchiveReceipt.class)
                            .queryForId("echo:" + MailIdentity.of(message(true, true)))
                            .getSource());
            assertNull(source.readOnly().get(1).getMessage().getMsgId());
            assertArrayEquals(header, Files.readAllBytes(Path.of(source.getPath() + ".jhr")));
            assertArrayEquals(index, Files.readAllBytes(Path.of(source.getPath() + ".jdx")));
            assertFalse(Files.exists(Path.of(source.getPath() + ".jlr")));
            JamArea netmail = new JamArea(root.resolve("netmail"), null, new FTNAddr("2:500/102"));
            netmail.create();
            netmail.append(message(false, true), JamArea.NET | JamArea.LOCAL);
            assertEquals(1, JamArchiveImporter.importInto(netmail, base).getImported());
            assertTrue(base.getQueuedNetmail().isEmpty());
            assertEquals(1, base.getEditorNetmail(new FTNAddr("2:500/102")).size());
            Tosser tosser = new Tosser(config, base);
            tosser.exportEchomail();
            tosser.exportPendingNetmail();
            assertTrue(new OutboundSpool(config).pending(config.getLink(new FTNAddr("2:500/101")),
                                                         true)
                                                .isEmpty());
        } finally {
            base.close();
        }
    }

    @Test
    public void retriesAfterPartialImportWithoutDuplicatingCommittedRecords() throws Exception {
        DatabaseManager base = new DatabaseManager(new OrmManager("jdbc:h2:mem:partialArchive"
                                                                  + System.nanoTime()));
        assertTrue(base.open());
        try {
            assertTrue(base.createArea("test"));
            JamArea source = new JamArea(temporary.getRoot().toPath().resolve("partial"),
                                         "test",
                                         new FTNAddr("2:500/102"));
            source.create();
            source.append(message(true, true), JamArea.ECHO | JamArea.LOCAL);
            source.append(message(true, false), JamArea.ECHO | JamArea.LOCAL);
            int[] seen = {0};
            try {
                JamArchiveImporter.importInto(source, (message, path) -> {
                    if (++seen[0] == 2) {
                        throw new IllegalStateException("Simulated interrupted import");
                    }
                    return base.importArchive(message, path);
                });
                throw new AssertionError("Expected interrupted import");
            } catch (IllegalStateException expected) {
                assertEquals(1, base.getMessageSize("test"));
            }
            JamArchiveImporter.Result result = JamArchiveImporter.importInto(source, base);
            assertEquals(1, result.getImported());
            assertEquals(1, result.getDuplicates());
            assertEquals(2, base.getMessageSize("test"));
        } finally {
            base.close();
        }
    }
}
