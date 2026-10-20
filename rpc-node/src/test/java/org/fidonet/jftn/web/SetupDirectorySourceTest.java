package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.NodeLookup;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.RpcAccounts;
import org.fidonet.nodelist.DirectoryCatalog;
import org.fidonet.nodelist.DirectorySource;
import org.fidonet.types.FTNAddr;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class SetupDirectorySourceTest {
    @TempDir
    Path root;

    private DirectorySource source(String id, Path path) {
        return new DirectorySource(id,
                                   DirectorySource.Format.NODELIST,
                                   path,
                                   1,
                                   500,
                                   StandardCharsets.US_ASCII,
                                   false);
    }

    @Test
    void firstSetupRespectsTheDirectoryProviderFragmentWithoutCreatingAH2Fallback()
            throws Exception {
        Files.createDirectory(root.resolve("conf.d"));
        Files.writeString(root.resolve("conf.d/05-storage.conf"),
                          "StorageEndpoint=directory|unavailable|opaque-location\n");
        NodeConfigurationService store = new NodeConfigurationService(root.resolve("jftn.conf"));
        assertThrows(IOException.class, store::directoryStorage);
        assertTrue(Files.notExists(root.resolve("database/nodelist/catalog.mv.db")));
    }

    private byte[] checked(String name) {
        String body = "Zone,1,Zone,City,Sysop,-Unpublished-,300\r\n"
                      + "Host,500,Net,City,Sysop,-Unpublished-,300\r\n"
                      + ",101,"
                      + name
                      + ",City,Sysop,-Unpublished-,300\r\n";
        int crc = 0;
        for (byte value : body.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (value & 255) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc << 1) ^ ((crc & 0x8000) != 0 ? 0x1021 : 0)) & 0xffff;
            }
        }
        return (";Nodelist : " + crc + "\r\n" + body).getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void replacesCanonicalNodesAndRemovesOnlyOldSetupAliasAfterSuccessfulDownload()
            throws Exception {
        NodeConfigurationService store = new NodeConfigurationService(root.resolve("jftn.conf"));
        Path initial = Files.writeString(root.resolve("old.lst"),
                                         ",101,Old,City,Sysop,-Unpublished-,300\r\n");
        try (DirectoryCatalog catalog = new DirectoryCatalog(store.catalogPath())) {
            for (String id : List.of("setup-nodelist", "nodes", "unrelated")) {
                catalog.importSource(source(id, initial));
            }
        }
        AtomicReference<byte[]> content = new AtomicReference<>(checked("Updated"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            byte[] bytes = content.get();
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (RpcAccounts accounts = new RpcAccounts(root.resolve("accounts"))) {
            NodeSettingsRpcHandler handler = new NodeSettingsRpcHandler(store,
                                                                        accounts,
                                                                        new UiSessions(accounts,
                                                                                       3600),
                                                                        root.resolve("accounts"),
                                                                        "setup",
                                                                        () -> {
                                                                        });
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/list";
            content.set("malformed publication".getBytes(StandardCharsets.US_ASCII));
            assertThrows(UiError.class,
                         () -> handler.lookup("setup", "1:500/101", url, "US-ASCII"));
            try (DirectoryCatalog catalog = new DirectoryCatalog(store.catalogPath())) {
                assertEquals(3, catalog.lookup(new FTNAddr("1:500/101")).size());
            }
            content.set(checked("Updated"));
            NodeLookup lookup = handler.lookup("setup", "1:500/101", url, "US-ASCII");
            assertTrue(lookup.isFound());
            try (DirectoryCatalog catalog = new DirectoryCatalog(store.catalogPath())) {
                assertTrue(catalog.lookup("setup-nodelist", new FTNAddr("1:500/101")).isEmpty());
                assertEquals("Updated",
                             catalog.lookup("nodes", new FTNAddr("1:500/101"))
                                    .orElseThrow()
                                    .getSystem());
                assertTrue(catalog.lookup("unrelated", new FTNAddr("1:500/101")).isPresent());
                assertEquals(2, catalog.lookup(new FTNAddr("1:500/101")).size());
            }
        } finally {
            server.stop(0);
        }
    }
}
