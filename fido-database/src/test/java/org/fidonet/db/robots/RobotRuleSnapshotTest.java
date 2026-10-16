package org.fidonet.db.robots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.OrmManager;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RobotRuleSnapshotTest {
    @TempDir
    Path root;

    private String main() {
        return "SysOp=Test\nLink=1:500/101,1:500/100,-\nInbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n";
    }

    private FileRobotRules read(String snapshot) throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream((main() + snapshot).getBytes(StandardCharsets.UTF_8)));
        return new FileRobotRules(config);
    }

    private RobotRules rules(List<RobotAccessRule> entries) {
        return service -> entries.stream()
                                 .filter(rule -> rule.getService() == service)
                                 .collect(Collectors.toList());
    }

    @Test
    void databaseToFilesAndBackRetainedCredentialsRightsMasksAndDisabledRules() throws Exception {
        OrmManager orm = new OrmManager("jdbc:h2:mem:snapshot" + System.nanoTime());
        orm.connect();
        try {
            verifyRoundTrip(new DatabaseRobotRules(orm));
        } finally {
            orm.disconnect();
        }
    }

    private void verifyRoundTrip(DatabaseRobotRules database) throws Exception {
        List<RobotAccessRule> original = List.of(RobotAccessRule.create(RobotService.AreaFix,
                                                                        "1:500/101",
                                                                        "area-secret",
                                                                        "test.*,local.news",
                                                                        "BOTH",
                                                                        true),
                                                 RobotAccessRule.create(RobotService.FileFix,
                                                                        "1:500/101",
                                                                        "file-secret",
                                                                        "lists",
                                                                        "",
                                                                        false),
                                                 RobotAccessRule.create(RobotService.Tic,
                                                                        "1:500/101",
                                                                        "TiC|#=päss",
                                                                        "lists,#with|delimiter",
                                                                        "",
                                                                        true));
        original.forEach(database::save);
        Path output = root.resolve("49-robot-access.conf");
        assertEquals(3, RobotRuleSnapshot.write(output, database));
        String text = Files.readString(output);
        assertFalse(text.contains("area-secret"));
        assertFalse(text.contains("file-secret"));
        assertFalse(text.contains("TiC|#=päss"));
        FileRobotRules loaded = read(text + "AreaFixPeer=ignored malformed legacy rule\n");
        for (RobotAccessRule expected : original) {
            RobotAccessRule actual = loaded.list(expected.getService()).get(0);
            assertEquals(expected.credential(), actual.credential());
            assertEquals(expected.getMasks(), actual.getMasks());
            assertEquals(expected.getAccess(), actual.getAccess());
            assertEquals(expected.isEnabled(), actual.isEnabled());
            database.delete(expected.getService(), new FTNAddr(expected.getAddress()));
        }
        FTNAddr peer = new FTNAddr("1:500/101");
        assertTrue(loaded.find(RobotService.AreaFix, peer).verifyPassword("area-secret"));
        assertFalse(loaded.find(RobotService.AreaFix, peer).verifyPassword("wrong"));
        assertNull(loaded.find(RobotService.FileFix, peer));
        assertTrue(loaded.list(RobotService.FileFix)
                         .get(0)
                         .withPolicy("lists", "", true)
                         .verifyPassword("file-secret"));
        assertEquals("TiC|#=päss", loaded.find(RobotService.Tic, peer).getDeliveryPassword());
        database.importIntoEmpty(loaded.all());
        assertTrue(database.find(RobotService.AreaFix, peer).verifyPassword("area-secret"));
        assertNull(database.find(RobotService.FileFix, peer));
        verifyPermissions(output);
    }

    private void verifyPermissions(Path output) throws Exception {
        if (Files.getFileStore(output).supportsFileAttributeView(PosixFileAttributeView.class)) {
            assertEquals(PosixFilePermissions.fromString("rw-------"),
                         Files.getPosixFilePermissions(output));
        }
    }

    @Test
    void emptySnapshotOverrodePreviousSnapshotAndLegacyRulesWithoutGrantingAccess()
            throws Exception {
        Files.writeString(root.resolve("jftn.conf"), main());
        Path fragments = Files.createDirectory(root.resolve("conf.d"));
        RobotRuleSnapshot.write(fragments.resolve("48-old.conf"),
                                rules(List.of(RobotAccessRule.create(RobotService.Tic,
                                                                     "1:500/101",
                                                                     "old",
                                                                     "*",
                                                                     "",
                                                                     true))));
        Files.writeString(fragments.resolve("41-areafix.conf"),
                          "AreaFixPeer=1:500/101|old|*|READ\n");
        RobotRuleSnapshot.write(fragments.resolve("49-empty.conf"), rules(List.of()));
        JFtnConfig config = new JFtnConfig();
        config.load(root.resolve("jftn.conf").toString());
        assertEquals("files", config.getValue("RobotAccessSource"));
        assertTrue(new FileRobotRules(config).all().isEmpty());
    }

    @Test
    void refusedOverwriteAndLeftNoTemporaryFiles() throws Exception {
        Path output = Files.writeString(root.resolve("existing.conf"), "keep original");
        assertThrows(FileAlreadyExistsException.class,
                     () -> RobotRuleSnapshot.write(output, rules(List.of())));
        assertEquals("keep original", Files.readString(output));
        try (Stream<Path> files = Files.list(root)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void malformedSnapshotsFailedClosedWithoutIncludingCredentialsInErrors() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> read("RobotAccessFormat=2\n"));
        assertThrows(IllegalArgumentException.class, () -> read("RobotAccessRule=none\n"));
        String secret = Base64.getEncoder()
                              .encodeToString("not-a-verifier".getBytes(StandardCharsets.UTF_8));
        String invalid = "RobotAccessFormat=1\nRobotAccessRule=AreaFix|1:500/101|"
                         + secret
                         + "|Kg==|READ|true\n";
        Exception failure = assertThrows(IllegalArgumentException.class, () -> read(invalid));
        assertFalse(failure.getMessage().contains(secret));
        assertNull(failure.getCause());
        assertThrows(IllegalArgumentException.class, () -> read(invalid.replace("true", "yes")));
        String valid = "RobotAccessFormat=1\nRobotAccessRule=Tic|1:500/101|c2VjcmV0|Kg==||true\n";
        assertThrows(IllegalArgumentException.class, () -> read(valid + valid));
        assertThrows(IllegalArgumentException.class,
                     () -> read(valid.replace("1:500/101", "1:500/999")));
    }
}
