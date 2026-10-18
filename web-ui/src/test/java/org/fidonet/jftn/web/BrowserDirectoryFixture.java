package org.fidonet.jftn.web;

import org.fidonet.nodelist.DirectoryCatalog;
import org.fidonet.nodelist.DirectorySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class BrowserDirectoryFixture {
    private BrowserDirectoryFixture() {
    }

    static Path create(Path directory) throws Exception {
        Path path = directory.resolve("database/nodelist/catalog");
        Path nodes = directory.resolve("nodes.lst");
        StringBuilder body = new StringBuilder(",101,Kyiv_Station,Kyiv,Jane_Doe,-Unpublished-,300,INA:192.0.2.10,IBN\n");
        for (int node = 200; node < 230; node++) {
            body.append(",")
                .append(node)
                .append(",Station_")
                .append(node)
                .append(",City,Sysop,-Unpublished-,300\n");
        }
        Files.writeString(nodes, body);
        Path points = directory.resolve("points.lst");
        Files.writeString(points,
                          "Boss,1:500/100\n,1,First_Point,Lviv,Point_Owner,-Unpublished-,300\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(path)) {
            catalog.importSource(new DirectorySource("nodes",
                                                     DirectorySource.Format.NODELIST,
                                                     nodes,
                                                     1,
                                                     500,
                                                     StandardCharsets.UTF_8,
                                                     false));
            catalog.importSource(new DirectorySource("points",
                                                     DirectorySource.Format.BOSS,
                                                     points,
                                                     1,
                                                     500,
                                                     StandardCharsets.UTF_8,
                                                     false));
        }
        return path;
    }
}
