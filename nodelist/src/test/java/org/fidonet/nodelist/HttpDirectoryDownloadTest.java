package org.fidonet.nodelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.types.FTNAddr;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class HttpDirectoryDownloadTest {
    @TempDir
    Path root;

    @Test
    void downloadsImportsAndKeepsThePreviousEditionOnMalformedResponses() throws Exception {
        byte[] valid = ",100,My_Node,Kyiv,Sysop,-Unpublished-,300,IBN\r\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            exchange.sendResponseHeaders(200, valid.length);
            exchange.getResponseBody().write(valid);
            exchange.close();
        });
        server.createContext("/bad", exchange -> {
            byte[] bytes = "<html>unavailable</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/list");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try (DirectoryCatalog catalog = new DirectoryCatalog(root.resolve("catalog"))) {
            DirectorySource source = new DirectorySource("setup",
                                                         DirectorySource.Format.NODELIST,
                                                         root.resolve("nodelist"),
                                                         1,
                                                         500,
                                                         StandardCharsets.UTF_8,
                                                         false);
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals(1,
                         HttpDirectoryDownload.install(catalog,
                                                       source,
                                                       URI.create(base + "/redirect"),
                                                       root.resolve("received"))
                                              .getRecords());
            assertEquals(new String(valid, StandardCharsets.UTF_8),
                         Files.readString(archived().get(0)));
            String old = Files.readString(source.getPath());
            assertThrows(IOException.class,
                         () -> HttpDirectoryDownload.install(catalog,
                                                             source,
                                                             URI.create(base + "/bad"),
                                                             root.resolve("received")));
            assertEquals(2, archived().size());
            assertEquals(old, Files.readString(source.getPath()));
            assertEquals("My_Node",
                         catalog.lookup("setup", new FTNAddr("1:500/100"))
                                .orElseThrow()
                                .getSystem());
        } finally {
            server.stop(0);
        }
    }

    private List<Path> archived() throws IOException {
        try (Stream<Path> paths = Files.walk(root.resolve("received"))) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    @Test
    void rejectsUnsupportedSchemesBeforeCreatingAnyFiles() {
        DirectorySource source = new DirectorySource("setup",
                                                     DirectorySource.Format.NODELIST,
                                                     root.resolve("base/nodelist"),
                                                     1,
                                                     500,
                                                     StandardCharsets.UTF_8,
                                                     false);
        assertThrows(IllegalArgumentException.class,
                     () -> HttpDirectoryDownload.install(null,
                                                         source,
                                                         URI.create("file:///etc/passwd")));
        assertFalse(Files.exists(source.getPath().getParent()));
    }
}
