package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class JamArchiveImportTest {
    @TempDir
    public File temporary;

    @Test
    public void commandImportsIntoFileDatabaseWithoutStartingTransport() throws Exception {
        Path root = temporary.toPath();
        String directory = root.toString().replace('\\', '/');
        Path configuration = root.resolve("node.conf");
        Files.writeString(configuration,
                          "Inbound="
                          + directory
                          + "/in\nTmp="
                          + directory
                          + "/state\nOutbound="
                          + directory
                          + "/out\nEchoPath="
                          + directory
                          + "/echo\nSysOp=Test\nLink=2:500/101,2:500/102,secret,localhost\n");
        String url = "jdbc:h2:" + directory + "/database";
        DatabaseManager base = new DatabaseManager(new OrmManager(url));
        assertTrue(base.open());
        try {
            assertTrue(base.createArea("test"));
        } finally {
            base.close();
        }
        JamArea source = new JamArea(root.resolve("source"), "test", new FTNAddr("2:500/102"));
        source.create();
        Message message = new Message("Sysop",
                                      "All",
                                      new FTNAddr("2:500/102"),
                                      new FTNAddr("2:500/101"),
                                      "Old mail",
                                      "AREA:TEST\rHello\r",
                                      new Date());
        source.append(message, JamArea.ECHO | JamArea.LOCAL);
        JamArchiveImport.main(new String[]{configuration.toString(), url,
                source.getPath().toString(), "test", "2:500/102"});
        DatabaseManager reopened = new DatabaseManager(new OrmManager(url));
        assertTrue(reopened.open());
        try {
            assertEquals(1, reopened.getMessageSize("test"));
            assertTrue(reopened.isArchived(message));
            assertTrue(reopened.getQueuedNetmail().isEmpty());
        } finally {
            reopened.close();
        }
        assertTrue(Files.notExists(root.resolve("out/jftn-mail")));
    }
}
