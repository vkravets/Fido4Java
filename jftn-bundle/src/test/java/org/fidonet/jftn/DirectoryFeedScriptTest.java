package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.zip.CRC32;

class DirectoryFeedScriptTest {
    @TempDir
    Path root;

    private JFtnConfig config() throws Exception {
        String path = root.toString().replace('\\', '/');
        String text = "FileEchoSource=files\nRobotAccessSource=files\nSysOp=Test\nInbound="
                      + path
                      + "/in\nOutbound="
                      + path
                      + "/out\nTmp="
                      + path
                      + "/state\nEchoPath="
                      + path
                      + "/echo\n"
                      + "Link=1:500/101,1:500/100,secret,localhost\nJamBridgeEnabled=false\n"
                      + "MailScript=classpath:mail-scripts/filefix.groovy\n"
                      + "MailScript=classpath:mail-scripts/directory-import.groovy\n"
                      + "FileArea=lists|"
                      + path
                      + "/received-lists\n"
                      + "TicPeer=1:500/101|tic-password|lists\n"
                      + "NodeDirectoryPath="
                      + path
                      + "/catalog\n"
                      + "DirectoryFeed=nodes|NODELIST|lists|NODELIST.*|NODEDIFF.*|"
                      + path
                      + "/base/nodes.lst|1:500/101|1|500\n"
                      + "DirectoryFeed=points|BOSS|lists|POINTS.*|POINTDIF.*|"
                      + path
                      + "/base/points.lst|1:500/101\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private String checked(String body, int day) {
        int crc = 0;
        for (byte octet : body.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (octet & 255) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc & 0x8000) == 0 ? crc << 1 : (crc << 1) ^ 0x1021) & 65535;
            }
        }
        return ";A Day " + day + " : " + String.format("%05d", crc) + "\r\n" + body;
    }

    private void receive(JFtnConfig config, String name, String text, String password)
            throws Exception {
        Path inbox = Path.of(config.getInbound(),
                             "peers",
                             ReceivedFile.directoryName(new FTNAddr("1:500/101")));
        Files.createDirectories(inbox);
        Path payload = Files.createTempFile(inbox, "payload-", ".file");
        Files.writeString(payload, text);
        ReceivedFile.record(payload, name);
        CRC32 crc = new CRC32();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        crc.update(bytes);
        String ticket = "Area lists\r\nFile "
                        + name
                        + "\r\nOrigin 1:500/101\r\nFrom 1:500/101\r\nPw "
                        + password
                        + "\r\nCrc "
                        + String.format("%08X", crc.getValue())
                        + "\r\nSize "
                        + bytes.length
                        + "\r\nPath 1:500/101 1700000000\r\nSeenby 1:500/101\r\n";
        Path tic = Files.createTempFile(inbox, "ticket-", ".file");
        Files.writeString(tic, ticket);
        ReceivedFile.record(tic, "test.tic");
    }

    private String node(String source, String address) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:"
                                                                 + root.resolve("catalog"));
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT system_name FROM directory_entries WHERE source_id='"
                        + source + "' AND address='" + address + "'")) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    @Test
    void importsDeliveredNodeAndPointListsAndDiffsThroughTheBundledScriptsAndSurvivesRestart()
            throws Exception {
        JFtnConfig config = config();
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:directoryFeed"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        String old = checked(",101,Old,City,Sysop,-Unpublished-,300\r\n", 288);
        String next = checked(",101,New,City,Sysop,-Unpublished-,300\r\n", 295);
        String points = checked("Boss,1:500/101\r\n,1,Point,City,Sysop,-Unpublished-,300\r\n", 288);
        try {
            try (MailRuntime service = new MailRuntime(config, base)) {
                // There is no base file at startup: the first complete delivery initializes it.
                receive(config, "NODELIST.288", old, "tic-password");
                receive(config, "POINTS.288", points, "tic-password");
                service.processPending();
                assertEquals("Old", node("nodes", "1:500/101.0"));
                assertEquals("Point", node("points", "1:500/101.1"));
                String patch = old.substring(0, old.indexOf('\r')) + "\r\nD2\r\nA2\r\n" + next;
                receive(config, "NODEDIFF.295", patch, "wrong-password");
                service.processPending();
                assertEquals("Old", node("nodes", "1:500/101.0"));
                receive(config, "NODEDIFF.295", patch, "tic-password");
                service.processPending();
                assertEquals("New", node("nodes", "1:500/101.0"));
            }
            try (MailRuntime service = new MailRuntime(config, base)) {
                service.processPending();
                assertEquals("New", node("nodes", "1:500/101.0"));
                assertEquals("Point", node("points", "1:500/101.1"));
            }
        } finally {
            base.close();
        }
    }
}
