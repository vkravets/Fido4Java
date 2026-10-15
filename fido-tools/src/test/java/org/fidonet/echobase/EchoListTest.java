package org.fidonet.echobase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class EchoListTest {
    @TempDir
    public File temporary;

    private File config(String text) throws IOException {
        File file = Files.createTempFile(temporary.toPath(), "test-", ".tmp").toFile();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void parsesOptionsWhitespaceCommentsAndFinalLink() throws Exception {
        File file = config("# comment\n"
                           + " \t # another comment\n\n"
                           + "Other ignored\n"
                           + "EchoAreas ignored\n"
                           + " \teChOaReA\tTEST   /echo/test -b JAM -g local -a 2:500/1.2 -unknown"
                           + " 2:500/2 # tail\n"
                           + "EchoArea SIMPLE /echo/simple 2:500/3\n");
        EchoList list = new EchoList(file.getPath());
        list.load();
        assertEquals(2, list.getEchoList().size());
        EchoCfg echo = list.getEcho("TEST");
        assertEquals("TEST", echo.Name);
        assertEquals("/echo/test", echo.Path);
        assertEquals("JAM", echo.Type);
        assertEquals("local", echo.Group);
        assertEquals(new FTNAddr("2:500/1.2"), echo.AKA);
        assertEquals(new FTNAddr("2:500/2"), echo.Link);
        assertEquals(new FTNAddr("2:500/3"), list.getEcho("SIMPLE").Link);
        assertNull(list.getEcho("missing"));
        assertFalse(list.isInList("missing"));
        List<String> names = list.getEchoList();
        names.clear();
        assertTrue(list.isInList("TEST"));
    }

    @Test
    public void addsAreasAndReloadsTheirCompleteConfiguration() throws Exception {
        File file = config("# existing content\n");
        EchoList list = new EchoList(file.getPath());
        assertTrue(list.getEchoList().isEmpty());
        FTNAddr link = new FTNAddr("2:500/2");
        FTNAddr aka = new FTNAddr("2:500/1");
        list.addArea("FIRST", temporary.getPath(), link, aka);
        list.addArea("SECOND", temporary.getPath(), link, aka);
        String content = Files.readString(file.toPath());
        assertTrue(content.startsWith("# existing content\n"));
        EchoList reopened = new EchoList(file.getPath());
        reopened.load();
        assertEquals(2, reopened.getEchoList().size());
        for (String name : new String[]{"FIRST", "SECOND"}) {
            EchoCfg echo = reopened.getEcho(name);
            assertEquals(new File(temporary, name).getPath(), echo.Path);
            assertEquals("JAM", echo.Type);
            assertEquals(link, echo.Link);
            assertEquals(aka, echo.AKA);
            assertNull(echo.Group);
        }
    }

    @Test
    public void groupedConfigurationSurvivesSerialization() throws Exception {
        EchoCfg original = new EchoCfg();
        original.Name = "GROUPED";
        original.Path = "/echo/grouped";
        original.Type = "JAM";
        original.Group = "local";
        original.AKA = new FTNAddr("2:500/1");
        original.Link = new FTNAddr("2:500/2");
        EchoList list = new EchoList(config(original.getEchoString()).getPath());
        list.load();
        assertEquals(original.Group, list.getEcho("GROUPED").Group);
        assertEquals(original.Link, list.getEcho("GROUPED").Link);
    }

    @Test
    public void reloadReplacesAreasAndLastDuplicateWins() throws Exception {
        EchoList list = new EchoList(config("EchoArea OLD /old\n").getPath());
        list.load();
        File replacement = config("EchoArea NEW /first\nEchoArea NEW /last\n");
        list.load(replacement.getPath());
        assertFalse(list.isInList("OLD"));
        assertEquals("/last", list.getEcho("NEW").Path);
        Files.writeString(replacement.toPath(), "EchoArea LATEST /latest\n");
        list.load();
        assertFalse(list.isInList("NEW"));
        assertTrue(list.isInList("LATEST"));
    }

    @Test
    public void malformedRecordsReportTheirLineAndPreserveLoadedAreas() throws Exception {
        File original = config("EchoArea ORIGINAL /original\n");
        EchoList list = new EchoList(original.getPath());
        list.load();
        for (String record : new String[]{"EchoArea", "EchoArea NAME", "EchoArea NAME /path -b",
                "EchoArea NAME /path -g", "EchoArea NAME /path -a",
                "EchoArea NAME /path -b -g local"}) {
            File invalid = config("# first line\n" + record + "\n");
            try {
                list.load(invalid.getPath());
                fail("Accepted malformed record: " + record);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains(invalid.getPath() + ":2"));
            }
            assertTrue(list.isInList("ORIGINAL"));
        }
        list.load(); // A failed load(filename) must not replace the working filename.
        assertTrue(list.isInList("ORIGINAL"));
    }

    @Test
    public void missingFileDoesNotDiscardLoadedAreas() throws Exception {
        File file = config("EchoArea ORIGINAL /original\n");
        EchoList list = new EchoList(file.getPath());
        list.load();
        Files.delete(file.toPath());
        try {
            list.load();
            fail("Missing file was accepted");
        } catch (IOException expected) {
            assertTrue(list.isInList("ORIGINAL"));
        }
    }

    @Test
    public void failedAppendDoesNotPublishAreaInMemory() throws Exception {
        EchoList list = new EchoList(new File(temporary, "missing/areas.cfg").getPath());
        try {
            list.addArea("FAILED", "/echo", new FTNAddr("2:500/2"), new FTNAddr("2:500/1"));
            fail("Missing parent directory was accepted");
        } catch (IOException expected) {
            assertFalse(list.isInList("FAILED"));
        }
    }
}
