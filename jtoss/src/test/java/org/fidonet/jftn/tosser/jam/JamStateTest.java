package org.fidonet.jftn.tosser.jam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class JamStateTest {
    @Test
    public void doesNotAdvanceStateWhenDurableWritingFails() throws Exception {
        Path path = temporary.getRoot().toPath().resolve("failed-state");
        JamState state = new JamState(path, (log, length, bytes) -> {
            throw new IOException("Disk failure");
        });
        try {
            state.prepare("message", "fingerprint", "2:500/1");
            fail("Expected disk failure");
        } catch (IOException expected) {
            assertEquals("Disk failure", expected.getMessage());
        }
        assertNull(state.stage("message"));
        assertNull(state.get("import.message"));
        assertNull(new JamState(path).stage("message"));
    }

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void recoversCommittedRecordsAndDropsAnIncompleteTail() throws Exception {
        Path path = temporary.getRoot().toPath().resolve("receipts.properties");
        Files.writeString(path, "legacy=retained\n");
        JamState state = new JamState(path);
        state.prepare("message", "fingerprint", "2:500/1");
        Path log = Path.of(path + ".log");
        Files.write(log, new byte[]{0, 0, 0, 100, 42}, StandardOpenOption.APPEND);
        JamState recovered = new JamState(path);
        assertEquals("retained", recovered.get("legacy"));
        assertEquals("PREPARED", recovered.get("stage.message"));
        assertEquals("2:500/1", recovered.get("targets.message"));
        recovered.put("stage.message", "ACCEPTED");
        assertEquals("ACCEPTED", new JamState(path).get("stage.message"));
        assertNull(recovered.get("missing"));
        assertEquals("legacy=retained\n", Files.readString(path));
    }

    @Test
    public void writtenBytesGrowLinearlyAndUnchangedValuesDoNotWrite() throws Exception {
        Path path = temporary.getRoot().toPath().resolve("state");
        JamState state = new JamState(path);
        appendReceipts(state, 0, 100);
        Path log = Path.of(path + ".log");
        long first = Files.size(log);
        appendReceipts(state, 100, 200);
        assertTrue(Files.size(log) < first * 3);
        long unchanged = Files.size(log);
        state.put("export.199", "true");
        assertEquals(unchanged, Files.size(log));
        assertEquals("true", new JamState(path).get("export.199"));
    }

    private void appendReceipts(JamState state, int start, int end) throws Exception {
        for (int number = start; number < end; number++) {
            state.put("export." + number, "true");
        }
    }
}
