package org.fidonet.jftn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.MailLinkConfiguration;
import org.fidonet.jftn.rpc.NodeConfiguration;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.transport.THttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

class SettingsHandlerTest {
    @Test
    void validatedEveryInitialUplinkBeforeCreatingAdminAndPublishedThemTogether() throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        NodeConfigurationStore store = new NodeConfigurationStore(config);
        AtomicBoolean completed = new AtomicBoolean();
        try (RpcAccounts accounts = new RpcAccounts(auth)) {
            SettingsHandler handler = new SettingsHandler(store,
                                                          accounts,
                                                          new UiSessions(accounts, 3600),
                                                          auth,
                                                          "setup",
                                                          () -> completed.set(true));
            MailLinkConfiguration first = initialLink("1:500/101", "first.example.org");
            MailLinkConfiguration second = initialLink("1:500/102",
                                                       "second.example.org").setSessionPassword("long-session-password")
                                                                            .setPacketPasswordMode("SEPARATE")
                                                                            .setPacketPassword("pktpass");
            NodeConfiguration value = valid().setInitialLinks(List.of(first, second));
            second.setPacketPassword("too-long-packet-password");
            assertThrows(UiError.class, () -> handler.save("setup", value));
            assertFalse(accounts.hasUsers());
            assertFalse(Files.exists(config));
            assertFalse(completed.get());
            second.setPacketPassword("pktpass");
            second.setAddress("1:500/101.0");
            assertThrows(UiError.class, () -> handler.save("setup", value));
            assertFalse(accounts.hasUsers());
            second.setAddress("1:500/102");
            assertTrue(handler.save("setup", value));
            assertTrue(completed.get());
            JFtnConfig parsed = store.configuration();
            assertEquals(2, parsed.getConfiguredLinks().size());
            assertEquals("pktpass",
                         parsed.getPacketPassword(parsed.getConfiguredLinks()
                                                        .stream()
                                                        .filter(link -> link.getAddr()
                                                                            .toString()
                                                                            .equals("1:500/102.0"))
                                                        .findFirst()
                                                        .orElseThrow()));
            List<MailLinkConfiguration> links = new MailConfigurationStore(store).links();
            assertEquals(List.of("1:500/101.0", "1:500/102.0"),
                         links.stream()
                              .map(MailLinkConfiguration::getAddress)
                              .collect(Collectors.toList()));
            assertTrue(links.stream()
                            .allMatch(link -> link.getSessionPassword().isEmpty()
                                    && link.getPacketPassword().isEmpty()));
            assertFalse(store.current().isSetInitialLinks());
            assertThrows(IllegalArgumentException.class, () -> store.save(value, auth));
            store.save(store.current().setSystem("Updated station"), auth);
            assertEquals(2, new MailConfigurationStore(store).links().size());
        }
    }

    @Test
    void rejectedMissingInitialLinksAndMismatchedStationAkas() throws Exception {
        NodeConfigurationStore store = new NodeConfigurationStore(directory.resolve("jftn.conf"));
        assertThrows(IllegalArgumentException.class,
                     () -> store.validate(valid().setInitialLinks(List.of())));
        MailLinkConfiguration first = initialLink("1:500/101", "first.example.org");
        first.setLocalAddresses(List.of("1:500/999"));
        assertThrows(IllegalArgumentException.class,
                     () -> store.validate(valid().setInitialLinks(List.of(first))));
        assertFalse(Files.exists(directory.resolve("jftn.conf")));
    }

    private MailLinkConfiguration initialLink(String address, String host) {
        return new MailLinkConfiguration(address,
                                         List.of("1:500/100", "1:500/100.1"),
                                         host,
                                         24554,
                                         "linkpass",
                                         "",
                                         "INHERIT");
    }

    @Test
    void refusedPublicationWhenFragmentsChangedDuringValidation() throws Exception {
        Path path = directory.resolve("jftn.conf");
        new NodeConfigurationStore(path).save(valid(), directory.resolve("accounts"));
        String original = Files.readString(path);
        Path fragment = Files.createDirectories(directory.resolve("conf.d"))
                             .resolve("99-manual.conf");
        Files.writeString(fragment, "Custom=old\n");
        NodeConfigurationStore store = new NodeConfigurationStore(path,
                                                                  (candidate, publication) -> {
                                                                      Files.writeString(fragment,
                                                                                        "Custom=changed-externally\n");
                                                                      publication.run();
                                                                  });
        assertThrows(IllegalArgumentException.class,
                     () -> store.save(valid().setSystem("Must not apply"),
                                      directory.resolve("accounts")));
        assertEquals(original, Files.readString(path));
        assertEquals("Custom=changed-externally\n", Files.readString(fragment));
    }
    @Test
    void restoredFileWhenRuntimePublicationFailedAndKeptRejectedCandidatesOffDisk()
            throws Exception {
        Path path = directory.resolve("jftn.conf");
        new NodeConfigurationStore(path).save(valid(), directory.resolve("accounts"));
        String original = Files.readString(path);
        NodeConfigurationStore store = new NodeConfigurationStore(path,
                                                                  (candidate, publication) -> {
                                                                      publication.run();
                                                                      throw new IOException("Simulated commit failure");
                                                                  });
        assertThrows(Exception.class,
                     () -> store.save(valid().setSystem("Changed"), directory.resolve("accounts")));
        assertEquals(original, Files.readString(path));
        NodeConfigurationStore rejected = new NodeConfigurationStore(path,
                                                                     (candidate, publication) -> {
                                                                         throw new IllegalArgumentException("Rejected before publication");
                                                                     });
        assertThrows(IllegalArgumentException.class,
                     () -> rejected.save(valid().setSystem("Rejected"),
                                         directory.resolve("accounts")));
        assertEquals(original, Files.readString(path));
    }
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
            assertThrows(UiError.class, () -> normal.links(point));
            assertThrows(UiError.class, () -> normal.tosser(point));
            assertThrows(UiError.class, () -> normal.saveLinks(point, List.of()));
            assertThrows(UiError.class, () -> normal.saveTosser(point, null));
            SettingsHandler setup = new SettingsHandler(new NodeConfigurationStore(config),
                                                        accounts,
                                                        sessions,
                                                        auth,
                                                        "resume",
                                                        () -> {
                                                        });
            assertThrows(UiError.class, () -> setup.links("resume"));
            assertThrows(UiError.class, () -> setup.tosser("resume"));
            NodeConfiguration wrong = valid().setPassword("another-password");
            assertThrows(UiError.class, () -> setup.save("resume", wrong));
            assertFalse(Files.exists(config));
            assertTrue(setup.save("resume", valid()));
        }
    }

    @Test
    void administratorSavedMailSettingsThroughTheSameStoreAsStationSettings() throws Exception {
        Path auth = directory.resolve("accounts");
        NodeConfigurationStore store = new NodeConfigurationStore(directory.resolve("jftn.conf"));
        store.save(valid(), auth);
        try (RpcAccounts accounts = new RpcAccounts(auth)) {
            accounts.createUser("admin", "a-long-password");
            UiSessions sessions = new UiSessions(accounts, 3600);
            String session = sessions.login(new Credential("admin", "a-long-password"))
                                     .getSession_id();
            SettingsHandler handler = new SettingsHandler(store,
                                                          accounts,
                                                          sessions,
                                                          auth,
                                                          "",
                                                          () -> {
                                                          });
            DefaultServerHandler handlers = new DefaultServerHandler() {
                @Override
                public SettingsService.Iface getSettingsHandler() {
                    return handler;
                }
            };
            try (HttpRpcServer server = new HttpRpcServer(0, "127.0.0.1", handlers, Set.of())) {
                server.run();
                try (THttpClient transport = new THttpClient("http://127.0.0.1:"
                        + server.getLocalPort() + "/rpc/settings")) {
                    SettingsService.Client client = new SettingsService.Client(new TJSONProtocol(transport));
                    var links = client.links(session);
                    links.get(0).setPacketPasswordMode("SEPARATE").setPacketPassword("pktpass");
                    assertTrue(client.saveLinks(session, links));
                    var tosser = client.tosser(session).setIntervalSeconds(20);
                    assertTrue(client.saveTosser(session, tosser));
                    assertTrue(client.save(session, client.current(session).setSystem("Changed")));
                    assertEquals(20, client.tosser(session).getIntervalSeconds());
                    assertEquals("SEPARATE", client.links(session).get(0).getPacketPasswordMode());
                    assertEquals("", client.links(session).get(0).getPacketPassword());
                    assertTrue(client.status().isRestartRequired());
                }
            }
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
    void savedRetentionToggleWithoutChangingFileOnlyConfiguration() throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        NodeConfigurationStore store = new NodeConfigurationStore(config);
        store.save(valid().setPreserveReceivedFiles(false), auth);
        assertFalse(store.current().isPreserveReceivedFiles());
        Files.createDirectory(directory.resolve("conf.d"));
        Path manual = directory.resolve("conf.d/manual.conf");
        String text = "# Never rewritten by UI\nReceivedArchivePath=private-archive\nCustomOption=preserved\n";
        Files.writeString(manual, text);
        NodeConfiguration edit = store.current().setPreserveReceivedFiles(true);
        store.save(edit, auth);
        assertTrue(store.current().isPreserveReceivedFiles());
        assertEquals(text, Files.readString(manual));
        JFtnConfig loaded = new JFtnConfig();
        loaded.load(config.toString());
        assertEquals(Path.of("private-archive"), loaded.getReceivedArchivePath());
        assertEquals("preserved", loaded.getValue("CustomOption"));
    }

    @Test
    void savedStationSettingsWithoutManagingPluginFilesOrReenablingRpc() throws Exception {
        Path config = directory.resolve("jftn.conf");
        Path auth = directory.resolve("accounts");
        new NodeConfigurationStore(config).save(valid(), auth);
        Files.writeString(config,
                          Files.readString(config).replace("rpcenabled=true", "rpcenabled=false"));
        Files.createDirectories(directory.resolve("conf.d"));
        Path jam = directory.resolve("conf.d/20-jam-bridge.conf");
        String jamText = "# Operator settings\nJamBridgeEnabled=false\nJamCharset=CP866\n";
        Files.writeString(jam, jamText);
        Path scripts = directory.resolve("conf.d/40-mail-scripts.conf");
        String scriptsText = "MailScript=custom.groovy\nMailScript=other.groovy\n";
        Files.writeString(scripts, scriptsText);
        NodeConfigurationStore store = new NodeConfigurationStore(config);
        store.save(store.current().setSystem("Edited station"), auth);
        assertEquals(jamText, Files.readString(jam));
        assertEquals(scriptsText, Files.readString(scripts));
        JFtnConfig loaded = new JFtnConfig();
        loaded.load(config.toString());
        assertEquals("false", loaded.getValue("RpcEnabled"));
        assertEquals("false", loaded.getValue("JamBridgeEnabled"));
        assertEquals(List.of("custom.groovy", "other.groovy"),
                     loaded.getValuesAsList("MailScript"));
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
