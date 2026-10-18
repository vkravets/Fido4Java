package org.fidonet.fileecho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

class FileEchoAreaServiceTest {
    @TempDir
    Path root;
    private MailDatabaseFacade base;
    private JFtnConfig config;
    private ScriptState state;
    private FileEchoAreaService store;
    private final FTNAddr first = new FTNAddr("1:500/101");
    private final FTNAddr other = new FTNAddr("1:500/102");

    @BeforeEach
    void open() throws Exception {
        config = configuration("");
        base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:fileecho" + System.nanoTime()));
        assertTrue(base.open());
        state = new ScriptState(root.resolve("state"));
        store = new FileEchoAreaService(config, base, state);
    }

    private JFtnConfig configuration(String additional) throws Exception {
        String text = "SysOp=Test\nInbound="
                      + root.resolve("in")
                      + "\nOutbound="
                      + root.resolve("out")
                      + "\nTmp="
                      + root.resolve("tmp")
                      + "\nEchoPath="
                      + root.resolve("echo")
                      + "\n"
                      + "Link=1:500/101,1:500/100,password,localhost\n"
                      + "Link=1:500/102,1:500/100,password,localhost\n"
                      + "FileEchoRoot="
                      + root.resolve("areas")
                      + "\n"
                      + additional;
        JFtnConfig value = new JFtnConfig();
        value.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return value;
    }

    @AfterEach
    void close() {
        if (base != null) {
            base.close();
        }
    }

    @Test
    void createsOnlyForAuthorizedLinkAndPreservesExistingRights() throws Exception {
        store.savePolicy(first.toString(), "lists.*", "WRITE");
        assertTrue(store.canReceive("lists.nodes", first));
        assertFalse(store.canReceive("lists.nodes", other));
        assertFalse(store.canReceive("private", first));
        assertNull(store.area("lists.nodes"));
        FileAreaRecord created = store.createAfterValidation("lists.nodes", first);
        assertEquals(root.resolve("areas/lists.nodes"), Path.of(created.directory));
        assertTrue(store.canReceive("lists.nodes", first));
        assertFalse(store.canReceive("lists.nodes", other));
        store.savePolicy(other.toString(), "*", "BOTH");
        assertFalse(store.canReceive("lists.nodes", other));
        assertEquals(1, store.access().size());
        assertThrows(Exception.class, () -> store.createAfterValidation("lists.nodes", other));
    }

    @Test
    void subscriptionsAndDisabledAreasSurviveRepositoryRestart() throws Exception {
        store.saveArea("lists", root.resolve("lists").toString(), "Lists", true);
        store.saveAccess("lists", first.toString(), "WRITE");
        store.subscribe(first, "lists", true);
        assertEquals("BOTH", store.access().get(0).access);
        assertEquals(Set.of("lists"), store.subscriptions(first));
        store.saveArea("lists", root.resolve("lists").toString(), "Disabled", false);
        FileEchoAreaService reopened = new FileEchoAreaService(config, base, state);
        assertFalse(reopened.canReceive("lists", first));
        assertEquals("Disabled", reopened.area("lists").description);
        reopened.subscribe(first, "lists", false);
        assertTrue(reopened.subscriptions(first).isEmpty());
    }

    @Test
    void validatesChangesWithoutPartialUpdates() throws Exception {
        assertThrows(IllegalArgumentException.class,
                     () -> store.saveArea("../outside", "somewhere", "", true));
        assertThrows(IllegalArgumentException.class,
                     () -> store.savePolicy("1:500/999", "*", "BOTH"));
        assertThrows(IllegalArgumentException.class,
                     () -> store.saveAccess("missing", first.toString(), "BOTH"));
        assertTrue(store.list().isEmpty());
        assertTrue(store.policies().isEmpty());
    }

    @Test
    void fileModeRetainsAutoCreatedAreasAndRejectsUiWrites() throws Exception {
        JFtnConfig files = configuration("FileEchoSource=files\nAutoCreateFileFrom=1:500/101|lists.*|WRITE\n");
        FileEchoAreaService original = new FileEchoAreaService(files, base, state);
        original.createAfterValidation("lists.nodes", first);
        FileEchoAreaService reopened = new FileEchoAreaService(files, base, state);
        assertEquals("lists.nodes", reopened.area("lists.nodes").name);
        assertTrue(reopened.canReceive("lists.nodes", first));
        assertFalse(reopened.canReceive("lists.nodes", other));
        assertThrows(IllegalStateException.class, () -> reopened.saveArea("new", "path", "", true));
    }
}
