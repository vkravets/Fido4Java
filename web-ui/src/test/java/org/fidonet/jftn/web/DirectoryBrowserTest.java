package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.DirectoryPage;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.RpcAccounts;
import org.fidonet.nodelist.DirectoryCatalog;
import org.fidonet.nodelist.DirectorySource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class DirectoryBrowserTest {
    @TempDir
    Path directory;

    @Test
    void projectsSourcesPointsEndpointsAndBoundedPagesWithoutAuthenticationOrSetupToken()
            throws Exception {
        Path catalogPath = directory.resolve("database/nodelist/catalog");
        Path source = directory.resolve("points.lst");
        Files.writeString(source,
                          "Boss,1:500/100\n,1,Point_Station,Kyiv,Point_Owner,-Unpublished-,300,IBN:mail.example.org:24555\n"
                                  + ",2,Other_Point,Lviv,Another_Owner,-Unpublished-,300\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(catalogPath)) {
            catalog.importSource(new DirectorySource("points",
                                                     DirectorySource.Format.BOSS,
                                                     source,
                                                     1,
                                                     500,
                                                     StandardCharsets.UTF_8,
                                                     false));
        }
        try (RpcAccounts accounts = new RpcAccounts(directory.resolve("accounts"))) {
            SettingsHandler handler = new SettingsHandler(new NodeConfigurationStore(directory.resolve("jftn.conf")),
                                                          accounts,
                                                          new UiSessions(accounts, 60),
                                                          directory.resolve("accounts"),
                                                          "private-setup",
                                                          () -> {
                                                          });
            DirectoryPage page = handler.searchDirectory("1:500/100", 0, 1, true);
            assertTrue(page.isHasMore());
            assertEquals(1, page.getNextOffset());
            assertEquals("POINT", page.getItems().get(0).getKind());
            assertEquals("Point Station", page.getItems().get(0).getSystem());
            assertEquals("points", page.getItems().get(0).getSource());
            assertEquals(List.of("mail.example.org:24555"), page.getItems().get(0).getEndpoints());
            assertFalse(handler.searchDirectory("1:500/100", 1, 1, true).isHasMore());
            assertTrue(handler.searchDirectory("1:500/100", 0, 1, false).getItems().isEmpty());
            assertTrue(handler.searchDirectory("Point", 0, 1, false).getItems().isEmpty());
            assertThrows(UiError.class, () -> handler.current(""));
            assertThrows(UiError.class, () -> handler.lookup("", "1:500/100", "", "UTF-8"));
            assertEquals("INVALID_ARGUMENT",
                         assertThrows(UiError.class,
                                      () -> handler.searchDirectory("", 0, 101, true)).getCode());
            assertFalse(Files.exists(directory.resolve("jftn.conf")));
            assertFalse(accounts.hasUsers());
        }
    }

    @Test
    void filtersPointsBeforeApplyingPageLimits() throws Exception {
        Path catalogPath = directory.resolve("paging");
        Path points = Files.writeString(directory.resolve("paging-points.lst"),
                                        "Boss,1:500/100\n,1,Point,City,Sysop,-Unpublished-,300\n"
                                        + ",2,Point,City,Sysop,-Unpublished-,300\n");
        Path nodes = Files.writeString(directory.resolve("paging-nodes.lst"),
                                       "Zone,1,Zone,City,Sysop,-Unpublished-,300\n"
                                       + "Host,500,Net,City,Sysop,-Unpublished-,300\n"
                                       + ",101,Node,City,Sysop,-Unpublished-,300\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(catalogPath)) {
            catalog.importSource(new DirectorySource("points",
                                                     DirectorySource.Format.BOSS,
                                                     points,
                                                     1,
                                                     500,
                                                     StandardCharsets.UTF_8,
                                                     false));
            catalog.importSource(new DirectorySource("nodes",
                                                     DirectorySource.Format.NODELIST,
                                                     nodes,
                                                     1,
                                                     500,
                                                     StandardCharsets.UTF_8,
                                                     false));
        }
        DirectoryPage page = DirectoryBrowser.search(catalogPath, "1:500/10", 0, 1, false);
        assertEquals("1:500/101.0", page.getItems().get(0).getAddress());
        assertFalse(page.isHasMore());
        assertEquals("1:500/101.0",
                     DirectoryBrowser.search(catalogPath, "Sysop", 0, 100, false)
                                     .getItems()
                                     .stream()
                                     .filter(node -> node.getAddress().equals("1:500/101.0"))
                                     .findFirst()
                                     .orElseThrow()
                                     .getAddress());
    }

    @Test
    void returnsAnEmptyPageBeforeAnyImport() throws Exception {
        assertTrue(DirectoryBrowser.search(directory.resolve("catalog"), "", 0, 25, true)
                                   .getItems()
                                   .isEmpty());
    }
}
