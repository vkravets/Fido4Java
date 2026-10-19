package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.FileEchoEvent;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

class FileEchoToolsTest {
    @TempDir
    Path root;

    private JFtnConfig config(String extra) throws Exception {
        String script = Path.of("src/main/resources/mail-scripts/fileecho-tools.groovy")
                            .toAbsolutePath()
                            .toString()
                            .replace('\\', '/');
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\nLink=1:500/101,1:500/100,secret,localhost\n"
                      + "MailScript="
                      + script
                      + "\n"
                      + extra;
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private MailDatabaseFacade database() {
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:tools"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        return base;
    }

    @Test
    void generatesIndexAndDeduplicatesLocalNotificationsAcrossRestart() throws Exception {
        JFtnConfig config = config("");
        Path area = Files.createDirectories(root.resolve("lists"));
        Path payload = Files.writeString(area.resolve("LIST.001"), "payload");
        Files.writeString(area.resolve("OTHER.ZIP"), "other");
        FTNAddr peer = new FTNAddr("1:500/101");
        FileEchoEvent accepted = new FileEchoEvent("accepted",
                                                   "lists",
                                                   payload,
                                                   "LIST.001",
                                                   peer,
                                                   "receipt",
                                                   List.of("Український опис",
                                                           "${name}\nsecond line"),
                                                   "",
                                                   123456L,
                                                   Instant.parse("1994-01-01T23:59:59Z"));
        FileEchoEvent duplicate = new FileEchoEvent("duplicate",
                                                    "lists",
                                                    payload,
                                                    "LIST.001",
                                                    peer,
                                                    "duplicate",
                                                    List.of(),
                                                    "Filename already exists");
        MailDatabaseFacade base = database();
        try {
            Tosser tosser = new Tosser(config, base);
            try (MailAutomationRuntime scripts = new MailAutomationRuntime(config, base, tosser)) {
                scripts.publishFileEcho(accepted);
                scripts.publishFileEcho(accepted);
                scripts.publishFileEcho(duplicate);
                scripts.publishFileEcho(duplicate);
            }
            List<String> rows = Files.readAllLines(area.resolve("files.bbs"));
            assertEquals(2, rows.size());
            assertTrue(rows.stream()
                           .anyMatch(row -> row.equals("OTHER.ZIP               5  -- -- --")));
            String indexed = rows.stream()
                                 .filter(row -> row.startsWith("LIST.001"))
                                 .findFirst()
                                 .orElseThrow();
            assertEquals("LIST.001          123,456  01-01-94  ", indexed.substring(0, 37));
            assertTrue(rows.stream()
                           .anyMatch(row -> row.contains("Український опис | ${name} second line")));
            FTNAddr local = new FTNAddr("1:500/100");
            assertEquals(1, base.getLocalNetmail(local).size());
            assertTrue(base.getLocalNetmail(local)
                           .get(0)
                           .getBody()
                           .getBody()
                           .contains("Filename already exists"));
            try (MailAutomationRuntime scripts = new MailAutomationRuntime(config, base, tosser)) {
                scripts.publishFileEcho(duplicate);
            }
            assertEquals(1, base.getLocalNetmail(local).size());
        } finally {
            base.close();
        }
    }

    @Test
    void refusesTemplatePathsOutsideTheScriptDirectory() throws Exception {
        JFtnConfig config = config("FileEchoNoticeTemplate=../outside.txt\n");
        MailDatabaseFacade base = database();
        try {
            assertThrows(Exception.class,
                         () -> new MailAutomationRuntime(config, base, new Tosser(config, base)));
        } finally {
            base.close();
        }
    }

    @Test
    void dropsIndexEntriesForFilesRemovedByReplaces() throws Exception {
        JFtnConfig config = config("FileEchoNotifyEnabled=false\n");
        Path area = Files.createDirectories(root.resolve("lists"));
        Files.writeString(area.resolve("files.bbs"), "OLD.001  old description\n");
        Path payload = Files.writeString(area.resolve("NEW.001"), "new");
        MailDatabaseFacade base = database();
        try (MailAutomationRuntime scripts = new MailAutomationRuntime(config,
                                                                       base,
                                                                       new Tosser(config, base))) {
            scripts.publishFileEcho(new FileEchoEvent("accepted",
                                                      "lists",
                                                      payload,
                                                      "NEW.001",
                                                      new FTNAddr("1:500/101"),
                                                      "replace-receipt",
                                                      List.of("new description"),
                                                      ""));
            List<String> rows = Files.readAllLines(area.resolve("files.bbs"));
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).startsWith("NEW.001"));
        } finally {
            base.close();
        }
    }
}
