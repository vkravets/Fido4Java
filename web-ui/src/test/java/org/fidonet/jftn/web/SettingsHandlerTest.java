package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.NodeConfiguration;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class SettingsHandlerTest {
    @TempDir
    Path directory;

    @Test
    void createdAdminAndPublishedValidConfigWithoutPlaintextLoginPassword() throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        AtomicBoolean completed = new AtomicBoolean();
        try (RpcAccounts accounts = new RpcAccounts(auth)) {
            SettingsHandler handler = new SettingsHandler(new NodeConfigurationStore(config),
                                                          accounts,
                                                          new UiSessions(accounts, 3600),
                                                          auth,
                                                          "secret",
                                                          () -> completed.set(true));
            assertThrows(UiError.class, () -> handler.current("wrong"));
            assertTrue(handler.save("secret", valid()));
            assertTrue(completed.get());
            assertTrue(accounts.verifyPassword("admin", "a-long-password"));
            assertFalse(handler.status().isRequired());
            assertThrows(UiError.class, () -> handler.current("secret"));
            assertFalse(Files.readString(config).contains("a-long-password"));
            JFtnConfig parsed = new JFtnConfig();
            parsed.load(config.toString());
            assertEquals("My node", parsed.getValue("System"));
            assertEquals("", new NodeConfigurationStore(config).current().getLinkPassword());
        }
    }

    @Test
    void refusedInjectionAndExternalEditsAndPreservedUnrelatedSettings() throws Exception {
        Path config = directory.resolve("jftn.conf");
        NodeConfigurationStore store = new NodeConfigurationStore(config);
        NodeConfiguration value = valid();
        value.setSystem("bad\nRpcEnabled=false");
        assertThrows(IllegalArgumentException.class,
                     () -> store.save(value, directory.resolve("accounts")));
        assertFalse(Files.exists(config));
        store.save(valid(), directory.resolve("accounts"));
        Files.writeString(config,
                          Files.readString(config) + "# external edit\nCustomOption=retained\n");
        assertThrows(Exception.class, () -> store.save(valid(), directory.resolve("accounts")));
        NodeConfigurationStore refreshed = new NodeConfigurationStore(config);
        refreshed.save(valid(), directory.resolve("accounts"));
        assertTrue(Files.readString(config).contains("CustomOption=retained"));
    }

    @Test
    void protectedSettingsFromPointAccountsAndResumedInterruptedSetupOnlyWithAdminPassword()
            throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        try (RpcAccounts accounts = new RpcAccounts(auth)) {
            accounts.createUser("admin", "a-long-password");
            AccountDirectory users = new AccountDirectory(accounts);
            users.register("point", "point-password", "1:500/100.1");
            users.decide("point", "1:500/100.1", true);
            UiSessions sessions = new UiSessions(accounts, 3600);
            SettingsHandler normal = new SettingsHandler(new NodeConfigurationStore(config),
                                                         accounts,
                                                         sessions,
                                                         auth,
                                                         "",
                                                         () -> {
                                                         });
            String point = sessions.login(new Credential("point", "point-password"))
                                   .getSession_id();
            assertThrows(UiError.class, () -> normal.current(point));
            SettingsHandler setup = new SettingsHandler(new NodeConfigurationStore(config),
                                                        accounts,
                                                        sessions,
                                                        auth,
                                                        "resume",
                                                        () -> {
                                                        });
            NodeConfiguration wrong = valid().setPassword("another-password");
            assertThrows(UiError.class, () -> setup.save("resume", wrong));
            assertFalse(Files.exists(config));
            assertTrue(setup.save("resume", valid()));
        }
    }

    private NodeConfiguration valid() {
        return new NodeConfiguration("1:500/100",
                                     List.of("1:500/100.1"),
                                     "My node",
                                     "Sysop",
                                     "Kyiv",
                                     "1:500/101",
                                     "example.org",
                                     24554,
                                     "linkpass",
                                     directory.resolve("mail").toString(),
                                     "",
                                     "admin",
                                     "a-long-password",
                                     "CP866",
                                     "http://localhost:9091");
    }

    @Test
    void editedTheFirstLinkPreservedOtherLinksAndEnabledLocalhostPasskeys() throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        new NodeConfigurationStore(config).save(valid(), auth);
        String other = "Link=1:500/102,1:500/200,otherpwd,other.example:24554";
        Files.writeString(config, Files.readString(config) + other + "\n");
        NodeConfigurationStore store = new NodeConfigurationStore(config);
        NodeConfiguration edit = store.current();
        assertEquals("1:500/101.0", edit.getUplink());
        edit.setSystem("Updated");
        store.save(edit, auth);
        JFtnConfig parsed = new JFtnConfig();
        parsed.load(config.toString());
        assertEquals("linkpass",
                     parsed.getConfiguredLinks()
                           .stream()
                           .filter(link -> link.getAddr().toString().equals("1:500/101.0"))
                           .findFirst()
                           .orElseThrow()
                           .getPass());
        assertTrue(Files.readString(config).contains(other));
        assertEquals("true", parsed.getValue("RpcPasskeysEnabled"));
        assertEquals("localhost", parsed.getValue("RpcWebAuthnRpId"));
    }

    @Test
    void didNotReopenSetupWhenTheStoredAdminHadNoConfigPassword() throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        new NodeConfigurationStore(config).save(valid(), auth);
        try (RpcAccounts accounts = new RpcAccounts(auth)) {
            accounts.createUser("admin", "a-long-password");
        }
        InitialSetup.prepare(config);
        try (RpcAccounts accounts = new RpcAccounts(auth)) {
            assertTrue(accounts.verifyPassword("admin", "a-long-password"));
        }
    }

    @Test
    void acceptedSameOriginSetupAndRejectedForeignOriginsOverRealHttp() throws Exception {
        try (RpcAccounts accounts = new RpcAccounts(directory.resolve("accounts"))) {
            SettingsHandler settings = new SettingsHandler(new NodeConfigurationStore(directory.resolve("jftn.conf")),
                                                           accounts,
                                                           new UiSessions(accounts, 3600),
                                                           directory.resolve("accounts"),
                                                           "token",
                                                           () -> {
                                                           });
            DefaultServerHandler handlers = new DefaultServerHandler() {
                @Override
                public SettingsService.Iface getSettingsHandler() {
                    return settings;
                }
            };
            try (HttpRpcServer server = new HttpRpcServer(0, "127.0.0.1", handlers, Set.of())) {
                server.run();
                String origin = "http://localhost:" + server.getLocalPort();
                URI endpoint = URI.create("http://127.0.0.1:" + server.getLocalPort()
                        + "/rpc/settings");
                String request = "[1,\"status\",1,1,{}]";
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest allowed = HttpRequest.newBuilder(endpoint)
                                                 .header("Origin", origin)
                                                 .header("Content-Type",
                                                         "application/vnd.apache.thrift.json")
                                                 .POST(HttpRequest.BodyPublishers.ofString(request))
                                                 .build();
                HttpResponse<String> response = client.send(allowed,
                                                            HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("status"));
                HttpRequest denied = HttpRequest.newBuilder(endpoint)
                                                .header("Origin", "http://foreign.example")
                                                .header("Content-Type",
                                                        "application/vnd.apache.thrift.json")
                                                .POST(HttpRequest.BodyPublishers.ofString(request))
                                                .build();
                assertEquals(403,
                             client.send(denied, HttpResponse.BodyHandlers.discarding())
                                   .statusCode());
            }
        }
    }
}
