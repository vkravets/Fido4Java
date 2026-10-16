package org.fidonet.jftn.rpc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.PasskeyOptions;
import org.fidonet.jftn.rpc.Response;
import org.fidonet.jftn.rpc.Server;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

class AccountAuthenticationTest {
    private static final String origin = "https://node.example";
    @TempDir
    Path root;
    private RpcAccounts accounts;
    private UiSessions sessions;
    private AccountAuthentication auth;
    private String token;
    private final AtomicLong clock = new AtomicLong(1000);

    @BeforeEach
    void open() throws Exception {
        accounts = new RpcAccounts(root.resolve("accounts"));
        accounts.bootstrap("sysop", "password");
        sessions = new UiSessions(accounts, 3600, clock::get);
        auth = new AccountAuthentication(accounts,
                                         sessions,
                                         "node.example",
                                         Set.of(origin),
                                         clock::get);
        token = sessions.login(new Credential("sysop", "password")).getSession_id();
    }

    @AfterEach
    void close() throws Exception {
        auth.close();
        accounts.close();
    }

    private SoftwarePasskey enroll() throws Exception {
        PasskeyOptions options = auth.beginPasskeyRegistration(token, "password", "Laptop");
        SoftwarePasskey key = new SoftwarePasskey(options);
        auth.finishPasskeyRegistration(token,
                                       options.getRequestId(),
                                       key.registration(options, origin, true));
        return key;
    }

    @Test
    void rejectsRegistrationWithoutUserVerificationOrFromAnUntrustedOrigin() throws Exception {
        PasskeyOptions noVerification = auth.beginPasskeyRegistration(token, "password", "Laptop");
        SoftwarePasskey first = new SoftwarePasskey(noVerification);
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyRegistration(token,
                                                          noVerification.getRequestId(),
                                                          first.registration(noVerification,
                                                                             origin,
                                                                             false)));
        PasskeyOptions wrongOrigin = auth.beginPasskeyRegistration(token, "password", "Laptop");
        SoftwarePasskey second = new SoftwarePasskey(wrongOrigin);
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyRegistration(token,
                                                          wrongOrigin.getRequestId(),
                                                          second.registration(wrongOrigin,
                                                                              "https://evil.example",
                                                                              true)));
        assertTrue(auth.passkeys(token).isEmpty());
    }

    @Test
    void registersAndAuthenticatesOverRealMultiplexedThriftTcp() throws Exception {
        try (Server server = new Server(0, "127.0.0.1", handlers())) {
            server.run();
            try (TFramedTransport connection = new TFramedTransport(new TSocket("127.0.0.1",
                                                                                server.getLocalPort(),
                                                                                5000))) {
                connection.open();
                AuthService.Client client = new AuthService.Client(new TMultiplexedProtocol(new TBinaryProtocol(connection),
                                                                                            "auth"));
                registerAndAuthenticate(client);
            }
        }
    }

    @Test
    void registersAndAuthenticatesOverRealJsonHttp() throws Exception {
        try (HttpRpcServer server = new HttpRpcServer(0, "127.0.0.1", handlers(), Set.of(origin))) {
            server.run();
            try (THttpClient connection = new THttpClient("http://127.0.0.1:"
                    + server.getLocalPort() + "/rpc/auth")) {
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setCustomHeader("Origin", origin);
                registerAndAuthenticate(new AuthService.Client(new TJSONProtocol(connection)));
            }
        }
    }

    private DefaultServerHandler handlers() {
        return new DefaultServerHandler() {
            @Override
            public AuthService.Iface getAuthHandler() {
                return auth;
            }

            @Override
            public LoginService.Iface getLoginHandler() {
                return sessions;
            }
        };
    }

    private void registerAndAuthenticate(AuthService.Client client) throws Exception {
        assertTrue(client.methods().isPasskey());
        PasskeyOptions registration = client.beginPasskeyRegistration(token, "password", "Laptop");
        SoftwarePasskey key = new SoftwarePasskey(registration);
        client.finishPasskeyRegistration(token,
                                         registration.getRequestId(),
                                         key.registration(registration, origin, true));
        PasskeyOptions login = client.beginPasskeyLogin();
        String session = client.finishPasskeyLogin(login.getRequestId(),
                                                   key.assertion(login, origin, 1, true))
                               .getSession_id();
        assertEquals("sysop", sessions.require(session));
    }

    @Test
    void acceptsZeroCountersUsedBySyncedPasskeys() throws Exception {
        SoftwarePasskey key = enroll();
        for (int attempt = 0; attempt < 2; attempt++) {
            PasskeyOptions options = auth.beginPasskeyLogin();
            assertEquals(Response.OK,
                         auth.finishPasskeyLogin(options.getRequestId(),
                                                 key.assertion(options, origin, 0, true))
                             .getRespose_status());
        }
    }

    @Test
    void registersSignsInWithoutPasswordAndPreservesKeysAcrossRestart() throws Exception {
        SoftwarePasskey key = enroll();
        assertEquals(1, auth.passkeys(token).size());
        auth.close();
        accounts.close();
        accounts = new RpcAccounts(root.resolve("accounts"));
        accounts.bootstrap("other", "must-not-replace-password");
        sessions = new UiSessions(accounts, 3600, clock::get);
        auth = new AccountAuthentication(accounts,
                                         sessions,
                                         "node.example",
                                         Set.of(origin),
                                         clock::get);
        PasskeyOptions options = auth.beginPasskeyLogin();
        var login = auth.finishPasskeyLogin(options.getRequestId(),
                                            key.assertion(options, origin, 1, true));
        assertEquals(Response.OK, login.getRespose_status());
        assertEquals("sysop", sessions.require(login.getSession_id()));
        assertEquals(Response.OK,
                     sessions.login(new Credential("sysop", "password")).getRespose_status());
        assertFalse(accounts.verifyPassword("sysop", "must-not-replace-password"));
    }

    @Test
    void rejectsReplaysWrongOriginsMissingUserVerificationBadSignaturesAndCounterRollback()
            throws Exception {
        SoftwarePasskey key = enroll();
        PasskeyOptions options = auth.beginPasskeyLogin();
        String signed = key.assertion(options, origin, 1, true);
        auth.finishPasskeyLogin(options.getRequestId(), signed);
        assertThrows(UiError.class, () -> auth.finishPasskeyLogin(options.getRequestId(), signed));
        PasskeyOptions wrongOrigin = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(wrongOrigin.getRequestId(),
                                                   key.assertion(wrongOrigin,
                                                                 "https://evil.example",
                                                                 2,
                                                                 true)));
        PasskeyOptions noVerification = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(noVerification.getRequestId(),
                                                   key.assertion(noVerification,
                                                                 origin,
                                                                 2,
                                                                 false)));
        PasskeyOptions counter = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(counter.getRequestId(),
                                                   key.assertion(counter, origin, 1, true)));
        PasskeyOptions differentChallenge = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(differentChallenge.getRequestId(), signed));
        PasskeyOptions badSignature = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(badSignature.getRequestId(),
                                                   key.corruptSignature(key.assertion(badSignature,
                                                                                      origin,
                                                                                      2,
                                                                                      true))));
    }

    @Test
    void requiresPasswordAndBoundSessionForEnrollmentAndConsumesFailedOrExpiredRequests()
            throws Exception {
        assertThrows(UiError.class, () -> auth.beginPasskeyRegistration(token, "wrong", "Laptop"));
        PasskeyOptions options = auth.beginPasskeyRegistration(token, "password", "Laptop");
        SoftwarePasskey key = new SoftwarePasskey(options);
        String other = sessions.login(new Credential("sysop", "password")).getSession_id();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyRegistration(other,
                                                          options.getRequestId(),
                                                          key.registration(options, origin, true)));
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyRegistration(token,
                                                          options.getRequestId(),
                                                          key.registration(options, origin, true)));
        PasskeyOptions expired = auth.beginPasskeyLogin();
        clock.addAndGet(120001);
        assertThrows(UiError.class, () -> auth.finishPasskeyLogin(expired.getRequestId(), "{}"));
        assertTrue(auth.passkeys(token).isEmpty());
    }

    @Test
    void storesSaltedPasswordHashesChangesPasswordAndRevokesSessions() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:"
                                                                 + root.resolve("accounts"));
                Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery("SELECT password_hash,user_handle FROM rpc_users WHERE login='sysop'")) {
            assertTrue(rows.next());
            assertTrue(rows.getString(1).startsWith("pbkdf2-sha256$600000$"));
            assertFalse(rows.getString(1).contains("password"));
            assertEquals(32, rows.getBytes(2).length);
        }
        auth.changePassword(token, "password", "replacement-password");
        assertThrows(UiError.class, () -> sessions.require(token));
        assertEquals(Response.AUTHORIZATION_FAILED,
                     sessions.login(new Credential("sysop", "password")).getRespose_status());
        assertEquals(Response.OK,
                     sessions.login(new Credential("sysop", "replacement-password"))
                             .getRespose_status());
    }

    @Test
    void scopesCredentialsToTheirOwnerAndRpAndRevocationKeepsPasswordLogin() throws Exception {
        SoftwarePasskey key = enroll();
        accounts.createUser("other", "other-password");
        PasskeyOptions foreignOwner = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(foreignOwner.getRequestId(),
                                                   key.withUserHandle(key.assertion(foreignOwner,
                                                                                    origin,
                                                                                    1,
                                                                                    true),
                                                                      accounts.handle("other")
                                                                              .orElseThrow()
                                                                              .getBase64Url())));
        String other = sessions.login(new Credential("other", "other-password")).getSession_id();
        assertTrue(auth.passkeys(other).isEmpty());
        auth.removePasskey(other, "other-password", key.id());
        assertEquals(1, auth.passkeys(token).size());
        try (AccountAuthentication foreignRp = new AccountAuthentication(accounts,
                                                                         sessions,
                                                                         "different.example",
                                                                         Set.of("https://different.example"))) {
            assertTrue(foreignRp.passkeys(token).isEmpty());
        }
        auth.removePasskey(token, "password", key.id());
        PasskeyOptions options = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(options.getRequestId(),
                                                   key.assertion(options, origin, 1, true)));
        assertEquals(Response.OK,
                     sessions.login(new Credential("sysop", "password")).getRespose_status());
    }

    @Test
    void disablingAnAccountRejectsExistingSessionsAndPasskeysAndInsecureOrigins() throws Exception {
        SoftwarePasskey key = enroll();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:"
                + root.resolve("accounts")); Statement sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE rpc_users SET enabled=FALSE WHERE login='sysop'");
        }
        assertThrows(UiError.class, () -> sessions.require(token));
        PasskeyOptions options = auth.beginPasskeyLogin();
        assertThrows(UiError.class,
                     () -> auth.finishPasskeyLogin(options.getRequestId(),
                                                   key.assertion(options, origin, 1, true)));
        assertThrows(IllegalArgumentException.class,
                     () -> new AccountAuthentication(accounts,
                                                     sessions,
                                                     "node.example",
                                                     Set.of("http://node.example")));
        assertThrows(IllegalArgumentException.class,
                     () -> new AccountAuthentication(accounts,
                                                     sessions,
                                                     "node.example",
                                                     Set.of("https://evil.example")));
    }
}
