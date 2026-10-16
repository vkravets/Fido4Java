package org.fidonet.jftn.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.auth.RpcAccounts;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Timeout(30)
class HttpRpcServerTest {
    @TempDir
    Path root;
    private RpcAccounts accounts;
    private HttpRpcServer http;
    private Server tcp;
    private final HttpClient browser = HttpClient.newBuilder()
                                                 .connectTimeout(Duration.ofSeconds(5))
                                                 .build();

    @BeforeEach
    void open() throws Exception {
        accounts = new RpcAccounts(root.resolve("accounts"));
        accounts.bootstrap("sysop", "secret");
        UiSessions sessions = new UiSessions(accounts, 60);
        DefaultServerHandler handlers = new DefaultServerHandler() {
            @Override
            public LoginService.Iface getLoginHandler() {
                return sessions;
            }

            @Override
            public UiService.Iface getUiHandler() {
                UiService.Iface backend = (UiService.Iface) Proxy.newProxyInstance(getClass().getClassLoader(),
                                                                                   new Class<?>[]{
                                                                                           UiService.Iface.class},
                                                                                   (proxy,
                                                                                    method,
                                                                                    args) -> {
                                                                                       if (method.getName()
                                                                                                 .equals("status")) {
                                                                                           return new NodeStatus("2.0",
                                                                                                                 1,
                                                                                                                 List.of((String) args[0]),
                                                                                                                 Long.MAX_VALUE,
                                                                                                                 0);
                                                                                       }
                                                                                       throw new IllegalArgumentException("unsupported test operation");
                                                                                   });
                return AuthenticatedUi.wrap(sessions, backend);
            }
        };
        http = new HttpRpcServer(0, "127.0.0.1", handlers, Set.of("http://localhost:5173"));
        tcp = new Server(0, "127.0.0.1", handlers);
        http.run();
        tcp.run();
    }

    @AfterEach
    void close() throws Exception {
        if (http != null) {
            http.close();
        }
        if (tcp != null) {
            tcp.close();
        }
        if (accounts != null) {
            accounts.close();
        }
    }

    @Test
    void sharesLoginAndLogoutBetweenJsonHttpAndBinaryTcp() throws Exception {
        try (THttpClient transport = httpTransport("login")) {
            LoginService.Client login = new LoginService.Client(new TJSONProtocol(transport));
            assertEquals(Response.AUTHORIZATION_FAILED,
                         login.login(new Credential("sysop", "wrong")).getRespose_status());
            LoginResponse result = login.login(new Credential("sysop", "secret"));
            assertEquals(Response.OK, result.getRespose_status());
            try (THttpClient uiTransport = httpTransport("ui")) {
                UiService.Client ui = new UiService.Client(new TJSONProtocol(uiTransport));
                assertEquals(List.of("sysop"),
                             ui.status(result.getSession_id()).getLocalAddresses());
                assertEquals(Long.MAX_VALUE, ui.status(result.getSession_id()).getHeapUsed());
                assertEquals("UNAUTHORIZED",
                             assertThrows(UiError.class, () -> ui.status("bad")).getCode());
                try (TFramedTransport connection = new TFramedTransport(new TSocket("127.0.0.1",
                                                                                    tcp.getLocalPort(),
                                                                                    5000))) {
                    connection.open();
                    LoginService.Client tcpLogin = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(connection),
                                                                                                    "login"));
                    tcpLogin.logout(result.getSession_id());
                    // The next call on this connection waits for the preceding oneway logout.
                    new Api.Client(new TMultiplexedProtocol(new TBinaryProtocol(connection),
                                                            "api")).ping();
                    assertEquals("UNAUTHORIZED",
                                 assertThrows(UiError.class,
                                              () -> ui.status(result.getSession_id())).getCode());
                }
            }
        }
    }

    @Test
    void appliesCorsAndRejectsUnsupportedRequests() throws Exception {
        HttpResponse<String> options = request("ui", "OPTIONS", "", "http://localhost:5173", null);
        assertEquals(204, options.statusCode());
        assertEquals("http://localhost:5173",
                     options.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(403,
                     request("login",
                             "POST",
                             "",
                             "https://evil.example",
                             "application/x-thrift").statusCode());
        assertEquals(403,
                     request("login", "POST", "", "null", "application/x-thrift").statusCode());
        assertEquals(405, request("login", "GET", "", null, null).statusCode());
        assertEquals(415, request("login", "POST", "{}", null, "text/plain").statusCode());
        assertEquals(404, request("absent", "POST", "", null, "application/x-thrift").statusCode());
        assertEquals(404,
                     request("login/extra", "POST", "", null, "application/x-thrift").statusCode());
        assertEquals(400,
                     request("login",
                             "POST",
                             "not-json",
                             null,
                             "application/x-thrift").statusCode());
        assertEquals(413,
                     request("login",
                             "POST",
                             "x".repeat(2 * 1024 * 1024 + 1),
                             null,
                             "application/x-thrift").statusCode());
    }

    @Test
    void closesBeforeStartAndReleasesTheListener() throws Exception {
        HttpRpcServer idle = new HttpRpcServer(0,
                                               "127.0.0.1",
                                               new DefaultServerHandler(),
                                               Set.of());
        int port = idle.getLocalPort();
        idle.close();
        idle.close();
        assertThrows(IllegalStateException.class, idle::run);
        try (ServerSocket socket = new ServerSocket(port)) {
            assertEquals(port, socket.getLocalPort());
        }
    }

    @Test
    void acceptsBrowserWireRequestsAndRejectsDuplicateStart() throws Exception {
        HttpResponse<String> response = request("api",
                                                "POST",
                                                "[1,\"ping\",1,0,{}]",
                                                "http://localhost:5173",
                                                "application/vnd.apache.thrift.json; charset=utf-8");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ping"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertThrows(IllegalStateException.class, http::run);
        assertThrows(IllegalArgumentException.class,
                     () -> new HttpRpcServer(0,
                                             "127.0.0.1",
                                             new DefaultServerHandler(),
                                             Set.of("*")));
    }

    private THttpClient httpTransport(String service) throws Exception {
        THttpClient transport = new THttpClient(url(service));
        transport.setConnectTimeout(5000);
        transport.setReadTimeout(5000);
        return transport;
    }

    private String url(String service) {
        return "http://127.0.0.1:" + http.getLocalPort() + "/rpc/" + service;
    }

    private HttpResponse<String> request(String service,
                                         String method,
                                         String body,
                                         String origin,
                                         String contentType)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(service)))
                                                 .timeout(Duration.ofSeconds(10))
                                                 .method(method,
                                                         HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
