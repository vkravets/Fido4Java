package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

class DirectorySearchTest {
    @TempDir
    Path directory;

    private void importList(DirectoryCatalog catalog,
                            String id,
                            DirectorySource.Format format,
                            String text)
            throws Exception {
        Path file = directory.resolve(id + ".lst");
        Files.writeString(file, text);
        catalog.importSource(new DirectorySource(id,
                                                 format,
                                                 file,
                                                 1,
                                                 500,
                                                 StandardCharsets.UTF_8,
                                                 false));
    }

    @Test
    void searchesAllSourcesAddressesNamesLocationsAndInternetAddressesWithStablePages()
            throws Exception {
        try (DirectoryCatalog catalog = new DirectoryCatalog(directory.resolve("catalog"))) {
            importList(catalog,
                       "nodes",
                       DirectorySource.Format.NODELIST,
                       ",101,Example_Station,Kyiv,Jane_Doe,-Unpublished-,300,INA:192.0.2.10,IBN\n"
                       + ",102,Other,City,John_Doe,000-192-0-2-20,300,IBN\n"
                       + ",103,IPv6,City,Alex,-Unpublished-,300,INA:[2001:db8::1],IBN\n"
                       + ",104,Українська_Станція,Київ,Олена_Сисоп,-Unpublished-,300\n");
            importList(catalog,
                       "points",
                       DirectorySource.Format.BOSS,
                       "Boss,1:500/101\n,1,Point_Station,Lviv,Point_Owner,-Unpublished-,300\n");
            importList(catalog,
                       "second",
                       DirectorySource.Format.NODELIST,
                       ",101,Alternate,City,Jane_Doe,-Unpublished-,300,IBN:mail.example.org\n");
            assertEquals(3, catalog.search("1:500/101", 0, 20).size());
            assertEquals(2, catalog.search("jAnE dOe", 0, 20).size());
            assertEquals("Example_Station",
                         catalog.search("example station", 0, 20).get(0).getSystem());
            assertEquals(1, catalog.search("kyiv", 0, 20).size());
            assertEquals(1, catalog.search("ОЛЕНА", 0, 20).size());
            assertEquals(1, catalog.search("українська станція", 0, 20).size());
            assertEquals(1, catalog.search("192.0.2.10", 0, 20).size());
            assertEquals(1, catalog.search("192.0.2.20", 0, 20).size());
            assertEquals(1, catalog.search("2001:db8::1", 0, 20).size());
            assertEquals(1, catalog.search("MAIL.EXAMPLE", 0, 20).size());
            assertEquals(1, catalog.search("1:500/101.1", 0, 20).size());
            List<DirectoryEntry> all = catalog.search("", 0, 20);
            List<DirectoryEntry> page = catalog.search("", 2, 2);
            assertEquals(all.subList(2, 4)
                            .stream()
                            .map(DirectoryEntry::getSystem)
                            .collect(Collectors.toList()),
                         page.stream().map(DirectoryEntry::getSystem).collect(Collectors.toList()));
            assertTrue(catalog.search("not present", 0, 20).isEmpty());
        }
    }

    @Test
    void treatsWildcardsAndSqlAsTextAndBoundsPublicQueries() throws Exception {
        try (DirectoryCatalog catalog = new DirectoryCatalog(directory.resolve("catalog"))) {
            importList(catalog,
                       "nodes",
                       DirectorySource.Format.NODELIST,
                       ",101,100%_Station,City,Jane_Doe,-Unpublished-,300\n");
            assertEquals(1, catalog.search("100%", 0, 20).size());
            assertTrue(catalog.search("' OR 1=1 --", 0, 20).isEmpty());
            assertTrue(catalog.search("!%", 0, 20).isEmpty());
            assertThrows(IllegalArgumentException.class,
                         () -> catalog.search("a".repeat(161), 0, 20));
            assertThrows(IllegalArgumentException.class, () -> catalog.search("", -1, 20));
            assertThrows(IllegalArgumentException.class, () -> catalog.search("", 1000001, 20));
            assertThrows(IllegalArgumentException.class, () -> catalog.search("", 0, 501));
        }
    }
}
