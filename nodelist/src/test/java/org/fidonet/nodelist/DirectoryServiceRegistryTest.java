package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.BaseConfig;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class DirectoryServiceRegistryTest {
    @TempDir
    Path root;

    private BaseConfig configuration(String extra) throws Exception {
        BaseConfig config = new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
        String text = "NodeDirectoryPath="
                      + root.resolve("catalog")
                      + "\nDirectorySource=nodes|NODELIST|"
                      + root.resolve("nodes.lst")
                      + "|1|500|UTF-8|false\n"
                      + extra;
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    @Test
    void ownsSourcesBeforeFirstDeliveryAndImportsThroughTheSharedApi() throws Exception {
        try (DirectoryService service = DirectoryService.open(configuration(""))) {
            assertTrue(service.hasSource("nodes"));
            assertFalse(service.isRefreshEnabled());
            service.claimDeliverySource("nodes");
            assertThrows(IllegalArgumentException.class,
                         () -> service.claimDeliverySource("nodes"));
            assertThrows(IllegalArgumentException.class,
                         () -> service.importFile("unknown", root, false));
            Path incoming = Files.writeString(root.resolve("incoming.lst"),
                                              ",101,Shared,City,Sysop,-Unpublished-,300\n");
            assertEquals(1, service.importFile("nodes", incoming, false).getRecords());
            assertEquals("Shared",
                         service.getCatalog().lookup(new FTNAddr("1:500/101")).get(0).getSystem());
        }
    }

    @Test
    void refreshesOnlyExplicitLocalSourcesAndRejectsConflictingOwners() throws Exception {
        Files.writeString(root.resolve("nodes.lst"), ",101,Local,City,Sysop,-Unpublished-,300\n");
        String extra = "DirectorySource=points|BOSS|"
                       + root.resolve("missing-points.lst")
                       + "\nDirectoryLocalSource=nodes\nDirectoryRefreshEnabled=true\n";
        try (DirectoryService service = DirectoryService.open(configuration(extra))) {
            assertEquals(1, service.refreshLocal().size());
            assertThrows(IllegalArgumentException.class,
                         () -> service.claimDeliverySource("nodes"));
            service.claimDeliverySource("points");
        }
    }

    @Test
    void rejectsSharedBasePathsAndInvalidLocalIdsBeforeOpeningCatalogStorage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                     () -> DirectoryService.open(configuration("DirectorySource=other|NODELIST|"
                                                               + root.resolve("nodes.lst")
                                                               + "\n")));
        assertThrows(IllegalArgumentException.class,
                     () -> DirectoryService.open(configuration("DirectoryLocalSource=missing\n")));
        assertFalse(Files.exists(root.resolve("catalog.mv.db")));
    }
}
