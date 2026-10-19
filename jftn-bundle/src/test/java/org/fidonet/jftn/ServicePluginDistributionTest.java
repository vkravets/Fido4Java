package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.Api;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.Response;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.packet.PacketWriter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/** Real installed JVMs with optional JARs physically removed, never hidden with test mocks. */
class ServicePluginDistributionTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @CsvSource({"full,true,true,true,true", "full,true,false,true,true",
            "full,false,false,true,true", "full,true,true,true,false", "full,true,true,false,true",
            "full,false,true,false,false", "headless,true,false,true,false",
            "headless,true,true,true,true"})
    void ranMailAndOnlyTheInstalledEnabledServices(String profile,
                                                   boolean rpcJar,
                                                   boolean uiJar,
                                                   boolean rpcEnabled,
                                                   boolean uiEnabled)
            throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        Files.writeString(home.resolve("runtime-profile"), profile + "\n");
        InstalledNodeFixture.configure(home, profile);
        configureRpc(home, rpcEnabled, uiEnabled, 0);
        if (!rpcJar) {
            removePlugin(home, "rpc-node-");
        }
        if (!uiJar) {
            removePlugin(home, "web-ui-");
        }
        Path packet = incoming(home);
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            verifyBinkp(node);
            node.await("Packet processed: .*handled=1");
            assertFalse(Files.exists(packet));
            if (rpcJar && rpcEnabled) {
                int tcp = node.port(InstalledNodeFixture.TCP);
                int http = node.port(InstalledNodeFixture.HTTP);
                verifyAuthenticatedRpc(tcp, http);
                HttpResponse<String> page = get(http);
                assertEquals(uiJar && uiEnabled ? 200 : 404, page.statusCode());
                if (uiJar && uiEnabled) {
                    assertTrue(page.body().contains("<div id=\"app\"></div>"));
                }
            } else {
                assertFalse(node.output().contains("RPC service listening:"));
                assertFalse(Files.exists(home.resolve("database/plugin-auth.mv.db")));
            }
        }
    }

    @Test
    void reportedMissingRpcBeforeOpeningStorageAndIgnoredDisabledUi() throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        InstalledNodeFixture.configure(home, "full");
        configureRpc(home, true, true, 0);
        removePlugin(home, "rpc-node-");
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            assertNotEquals(0, node.awaitExit());
            assertTrue(node.output()
                           .contains("Required service plugin is missing or disabled: rpc"));
            assertFalse(Files.exists(home.resolve("database/jftn10.mv.db")));
        }
        configureRpc(home, true, false, 0);
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            verifyBinkp(node);
        }
    }

    @Test
    void refusedRpcWithoutSqlBeforeOpeningAnAccountDatabase() throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        Files.writeString(home.resolve("runtime-profile"), "legacy\n");
        InstalledNodeFixture.configure(home, "legacy");
        configureRpc(home, true, true, 0);
        removePlugin(home, "fido-database-");
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            assertNotEquals(0, node.awaitExit());
            assertTrue(node.output().contains("RPC plugin requires SQL mail storage"));
            assertFalse(node.output().contains("NoClassDefFoundError"));
            assertFalse(Files.exists(home.resolve("database/plugin-auth.mv.db")));
            assertFalse(Files.exists(home.resolve("database/jftn10.mv.db")));
        }
    }

    @Test
    void respectedDisabledPluginFragmentsBeforeTheMainConfigurationExisted() throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        Path fragment = home.resolve("conf.d/95-web-ui.conf");
        Files.writeString(fragment, "WebUiEnabled=false\n");
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            assertNotEquals(0, node.awaitExit());
            assertTrue(node.output().contains("Create jftn.conf from jftn.conf.example"));
            assertFalse(node.output().contains("Configure JFtn:"));
            assertFalse(Files.exists(home.resolve("database/rpc-auth.mv.db")));
            assertEquals("WebUiEnabled=false\n", Files.readString(fragment));
        }
        Files.writeString(fragment, "WebUiEnabled=true\n");
        Files.writeString(home.resolve("conf.d/90-rpc.conf"), "RpcEnabled=false\n");
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            assertNotEquals(0, node.awaitExit());
            assertTrue(node.output().contains("Create jftn.conf from jftn.conf.example"));
            assertFalse(node.output().contains("Configure JFtn:"));
            assertFalse(Files.exists(home.resolve("database/rpc-auth.mv.db")));
        }
    }

    @Test
    void requiredManualConfigurationWhenSetupPluginWasAbsent() throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        removePlugin(home, "web-ui-");
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            assertNotEquals(0, node.awaitExit());
            assertTrue(node.output().contains("Create jftn.conf from jftn.conf.example"));
            assertFalse(Files.exists(home.resolve("database/jftn10.mv.db")));
            assertFalse(node.output().contains("Configure JFtn:"));
        }
    }

    @Test
    void startedWizardBeforeMailAndPreservedAnExistingPluginFragment() throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        Path custom = home.resolve("conf.d/operator.conf");
        Files.writeString(custom, "# Operator-owned settings\n");
        Files.writeString(home.resolve("conf.d/90-rpc.conf"),
                          "RpcAuthPath=./database/custom-setup-auth\n");
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            int port = node.port(InstalledNodeFixture.SETUP);
            assertEquals(200, get(port).statusCode());
            try (THttpClient connection = new THttpClient("http://127.0.0.1:" + port
                    + "/rpc/settings")) {
                assertTrue(new SettingsService.Client(new TJSONProtocol(connection)).status()
                                                                                    .isRequired());
            }
            assertFalse(Files.exists(home.resolve("database/jftn10.mv.db")));
            assertTrue(Files.exists(home.resolve("database/custom-setup-auth.mv.db")));
            assertFalse(Files.exists(home.resolve("database/rpc-auth.mv.db")));
            assertFalse(node.output().contains("binkp server listening:"));
            assertEquals("# Operator-owned settings\n", Files.readString(custom));
        }
    }

    @Test
    void rolledBackTcpAndOtherServicesWhenHttpBindingFailed() throws Exception {
        Path home = InstalledNodeFixture.unpack(temporary, "full");
        InstalledNodeFixture.configure(home, "full");
        try (ServerSocket occupied = new ServerSocket(0)) {
            configureRpc(home, true, true, occupied.getLocalPort());
            try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
                assertNotEquals(0, node.awaitExit());
                assertTrue(node.output().contains("BindException"));
                int releasedPort = node.port(InstalledNodeFixture.TCP);
                try (ServerSocket available = new ServerSocket(releasedPort)) {
                    assertTrue(available.isBound());
                }
            }
        }
        configureRpc(home, true, true, 0);
        try (InstalledNodeFixture.NodeProcess node = InstalledNodeFixture.start(home)) {
            verifyBinkp(node);
            verifyAuthenticatedRpc(node.port(InstalledNodeFixture.TCP),
                                   node.port(InstalledNodeFixture.HTTP));
        }
    }

    private void configureRpc(Path home, boolean rpc, boolean ui, int httpPort) throws Exception {
        Files.writeString(home.resolve("conf.d/90-rpc.conf"),
                          "RpcEnabled="
                          + rpc
                          + "\nRpcHttpEnabled=true\nRpcPort=0\nRpcHttpPort="
                          + httpPort
                          + "\nRpcUser=admin\nRpcPassword=test-only-password\n"
                          + "RpcAuthPath=./database/plugin-auth\n");
        Files.writeString(home.resolve("conf.d/95-web-ui.conf"), "WebUiEnabled=" + ui + "\n");
    }

    private void removePlugin(Path home, String prefix) throws Exception {
        List<Path> matches;
        try (var files = Files.list(home.resolve("plugins"))) {
            matches = files.filter(path -> path.getFileName().toString().startsWith(prefix))
                           .collect(Collectors.toList());
        }
        assertEquals(1, matches.size());
        Files.delete(matches.get(0));
    }

    private Path incoming(Path home) throws Exception {
        Path packet = home.resolve("mail/inbound/peers/1-500-101-0/00000001.pkt");
        Files.createDirectories(packet.getParent());
        Message message = new Message("Remote",
                                      "Sysop",
                                      new FTNAddr("1:500/101"),
                                      new FTNAddr("1:500/100"),
                                      "Plugin test",
                                      "\1MSGID: 1:500/101 12345678\rMail without optional services\r",
                                      new Date(1700000000000L));
        Files.write(packet,
                    PacketWriter.write(new FTNAddr("1:500/101"),
                                       new FTNAddr("1:500/100"),
                                       "",
                                       List.of(message)));
        return packet;
    }

    private void verifyBinkp(InstalledNodeFixture.NodeProcess node) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", node.port(InstalledNodeFixture.BINKP))) {
            socket.setSoTimeout(5000);
            assertTrue(socket.getInputStream().read() >= 0);
        }
    }

    private void verifyAuthenticatedRpc(int tcp, int http) throws Exception {
        try (TFramedTransport connection = new TFramedTransport(new TSocket("127.0.0.1",
                                                                            tcp,
                                                                            5000))) {
            connection.open();
            assertEquals(Response.OK,
                         new Api.Client(new TMultiplexedProtocol(new TBinaryProtocol(connection),
                                                                 "api")).ping());
            LoginService.Client login = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(connection),
                                                                                         "login"));
            String token = login.login(new Credential("admin", "test-only-password"))
                                .getSession_id();
            assertFalse(token.isEmpty());
            try (THttpClient httpConnection = new THttpClient("http://127.0.0.1:" + http
                    + "/rpc/ui")) {
                UiService.Client ui = new UiService.Client(new TJSONProtocol(httpConnection));
                assertFalse(ui.status(token).getLocalAddresses().isEmpty());
                assertThrows(UiError.class, () -> ui.status("invalid"));
            }
        }
    }

    private HttpResponse<String> get(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                                         .timeout(Duration.ofSeconds(5))
                                         .GET()
                                         .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
