package org.fidonet.jftn;

import org.fidonet.nodelist.DirectoryCatalog;
import org.fidonet.nodelist.DirectorySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VirtualDirectoryFixture {
    private VirtualDirectoryFixture() {
    }

    public static void seed(Path database) throws Exception {
        Files.createDirectories(database.getParent());
        Path source = database.getParent().resolve("virtual-nodes.lst");
        Files.writeString(source,
                          ",103,Remote,Test,Sysop,-Unpublished-,300,INA:127.0.0.1,IBN:24555\r\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(database)) {
            catalog.importSource(new DirectorySource("nodes",
                                                     DirectorySource.Format.NODELIST,
                                                     source,
                                                     1,
                                                     500,
                                                     StandardCharsets.US_ASCII,
                                                     false));
        }
    }
}
