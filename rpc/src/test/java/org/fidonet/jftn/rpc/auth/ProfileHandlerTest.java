package org.fidonet.jftn.rpc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.jftn.rpc.AuthenticatedUi;
import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.DefaultRpcServiceProvider;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.rpc.UiSessions;

import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.transport.THttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

class ProfileHandlerTest {
    @TempDir
    Path root;
    private RpcAccounts accounts;
    private UiSessions sessions;
    private ProfileHandler handler;
    private String admin;

    @BeforeEach
    void open() throws Exception {
        accounts = new RpcAccounts(root.resolve("accounts"));
        accounts.bootstrap("sysop", "admin-password");
        sessions = new UiSessions(accounts, 3600);
        admin = sessions.login(new Credential("sysop", "admin-password")).getSession_id();
        handler = new ProfileHandler(new AccountDirectory(accounts),
                                     sessions,
                                     List.of("1:500/100"),
                                     true);
    }

    @AfterEach
    void close() throws Exception {
        accounts.close();
    }

    private String point() throws Exception {
        handler.register("point", "point-password", "1:500/100.1");
        handler.decideAka(admin, "point", "1:500/100.1", true);
        return sessions.login(new Credential("point", "point-password")).getSession_id();
    }

    @Test
    void pointCanOnlyChangeItsOwnPreferencesAndRequestAddresses() throws Exception {
        String token = point();
        assertEquals("POINT", handler.me(token).getRole());
        handler.savePreferences(token, "dark", "1:500/100.1");
        assertEquals("dark", handler.me(token).getTheme());
        handler.requestAka(token, "1:500/100.2");
        assertEquals(2, handler.me(token).getClaimsSize());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class, () -> handler.accounts(token)).getCode());
        assertEquals("FORBIDDEN",
                     assertThrows(UiError.class,
                                  () -> handler.decideAka(token, "point", "1:500/100.2", true))
                                                                                               .getCode());
        assertEquals("INVALID_ARGUMENT",
                     assertThrows(UiError.class,
                                  () -> handler.savePreferences(token, "dark", "1:500/100.2"))
                                                                                              .getCode());
    }

    @Test
    void allExistingNodeWideUiMethodsRejectPointAccountsBeforeCallingTheBackend() throws Exception {
        String token = point();
        UiService.Iface backend = (UiService.Iface) Proxy.newProxyInstance(getClass().getClassLoader(),
                                                                           new Class<?>[]{
                                                                                   UiService.Iface.class},
                                                                           (proxy,
                                                                            method,
                                                                            args) -> {
                                                                               throw new AssertionError("Node-wide backend must not be called for a point");
                                                                           });
        UiService.Iface secured = AuthenticatedUi.wrap(sessions, backend);
        for (Method method : UiService.Iface.class.getMethods()) {
            Object[] arguments = arguments(method, token);
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                                                             () -> method.invoke(secured,
                                                                                 arguments));
            assertEquals("FORBIDDEN", ((UiError) failure.getCause()).getCode());
        }
    }

    private static Object[] arguments(Method method, String token) {
        Object[] result = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int i = 1; i < types.length; i++) {
            result[i] = types[i] == long.class
                    ? 0L
                    : types[i] == int.class ? 0 : types[i] == boolean.class ? false : null;
        }
        result[0] = token;
        return result;
    }

    @Test
    void profilesAndApprovalAreAvailableOverRealHttpJson() throws Exception {
        try (HttpRpcServer server = new HttpRpcServer(0,
                                                      "127.0.0.1",
                                                      new DefaultRpcServiceProvider() {
                                                          @Override
                                                          public ProfileService.Iface getProfileHandler() {
                                                              return handler;
                                                          }
                                                      },
                                                      Set.of())) {
            server.run();
            try (THttpClient connection = new THttpClient("http://127.0.0.1:"
                    + server.getLocalPort() + "/rpc/profile")) {
                ProfileService.Client client = new ProfileService.Client(new TJSONProtocol(connection));
                client.register("point", "point-password", "1:500/100.1");
                assertEquals(2, client.accounts(admin).size());
                client.decideAka(admin, "point", "1:500/100.1", true);
                assertEquals("ADMIN", client.me(admin).getRole());
            }
        }
    }
}
