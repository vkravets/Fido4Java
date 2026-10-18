package org.fidonet.jftn.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.auth.RpcAccounts;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class UiAuthenticationTest {
    @TempDir
    Path root;
    private RpcAccounts accounts;

    @BeforeEach
    void openAccounts() throws Exception {
        accounts = new RpcAccounts(root.resolve("accounts"));
        accounts.bootstrap("sysop", "secret");
    }

    @AfterEach
    void closeAccounts() throws Exception {
        accounts.close();
    }
    @Test
    void rejectsInvalidCredentialsExpiresSessionsAndInvalidatesLogout() throws Exception {
        AtomicLong clock = new AtomicLong(1000);
        UiSessions sessions = new UiSessions(accounts, 60, clock::get);
        assertEquals(Response.AUTHORIZATION_FAILED,
                     sessions.login(new Credential("sysop", "wrong")).getRespose_status());
        String first = sessions.login(new Credential("sysop", "secret")).getSession_id();
        String second = sessions.login(new Credential("sysop", "secret")).getSession_id();
        assertNotEquals(first, second);
        assertEquals("sysop", sessions.require(first));
        sessions.logout(first);
        assertThrows(UiError.class, () -> sessions.require(first));
        clock.addAndGet(60000);
        assertThrows(UiError.class, () -> sessions.require(second));
    }

    private UiService.Iface backend() {
        return (UiService.Iface) Proxy.newProxyInstance(getClass().getClassLoader(),
                                                        new Class<?>[]{UiService.Iface.class},
                                                        (proxy, method, args) -> {
                                                            if (method.getName().equals("status")) {
                                                                return new NodeStatus("2.0",
                                                                                      1,
                                                                                      List.of((String) args[0]),
                                                                                      1,
                                                                                      0);
                                                            }
                                                            throw new IllegalArgumentException("invalid");
                                                        });
    }

    @Test
    void allUiMethodsRequireSessionBeforeCallingTheBackend() throws Exception {
        UiService.Iface api = AuthenticatedUi.wrap(new UiSessions(accounts, 60), backend());
        for (Method method : UiService.Iface.class.getMethods()) {
            Object[] args = new Object[method.getParameterCount()];
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                args[i] = types[i] == long.class
                        ? 0L
                        : types[i] == int.class ? 0 : types[i] == boolean.class ? false : null;
            }
            args[0] = "invalid-session";
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                                                             () -> method.invoke(api, args),
                                                             method.getName());
            assertTrue(failure.getCause() instanceof UiError, method.getName());
            assertEquals("UNAUTHORIZED", ((UiError) failure.getCause()).getCode());
        }
    }

    @Test
    void dispatchesAuthenticatedUiOverRealFramedTcpAndReturnsTypedErrors() throws Exception {
        UiSessions sessions = new UiSessions(accounts, 60);
        UiService.Iface api = AuthenticatedUi.wrap(sessions, backend());
        try (Server server = new Server(0, "127.0.0.1", new DefaultRpcServiceProvider() {
            @Override
            public LoginService.Iface getLoginHandler() {
                return sessions;
            }

            @Override
            public UiService.Iface getUiHandler() {
                return api;
            }
        })) {
            server.run();
            try (TFramedTransport transport = new TFramedTransport(new TSocket("127.0.0.1",
                                                                               server.getLocalPort(),
                                                                               5000))) {
                transport.open();
                LoginService.Client login = new LoginService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                             "login"));
                UiService.Client ui = new UiService.Client(new TMultiplexedProtocol(new TBinaryProtocol(transport),
                                                                                    "ui"));
                assertEquals("UNAUTHORIZED",
                             assertThrows(UiError.class, () -> ui.status("bad")).getCode());
                String token = login.login(new Credential("sysop", "secret")).getSession_id();
                assertEquals(List.of("sysop"), ui.status(token).getLocalAddresses());
                assertEquals("INVALID_ARGUMENT",
                             assertThrows(UiError.class, () -> ui.areas(token)).getCode());
            }
        }
    }
}
