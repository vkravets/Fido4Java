package org.fidonet.jftn.web;

import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountAuthentication;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.ProfileHandler;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Isolated test accounts and real RPC handlers; never packaged in the application. */
final class BrowserServerFixture implements AutoCloseable {
    private final RpcAccounts accounts;
    private final AccountAuthentication authentication;
    private final HttpRpcServer server;

    BrowserServerFixture(Path directory) throws Exception {
        this(directory, "");
    }

    BrowserServerFixture(Path directory, String setupToken) throws Exception {
        Path authPath = directory.resolve("accounts");
        accounts = new RpcAccounts(authPath);
        if (setupToken.isEmpty()) {
            accounts.createUser("admin", "preview-admin-password");
        }
        UiSessions sessions = new UiSessions(accounts, 3600);
        authentication = new AccountAuthentication(accounts, sessions, "", Set.of());
        ProfileHandler profile = new ProfileHandler(new AccountDirectory(accounts),
                                                    sessions,
                                                    List.of("1:500/100"),
                                                    true);
        SettingsHandler settings = new SettingsHandler(new NodeConfigurationStore(directory.resolve("jftn.conf")),
                                                       accounts,
                                                       sessions,
                                                       authPath,
                                                       setupToken,
                                                       () -> {
                                                       });
        server = new HttpRpcServer(0,
                                   "127.0.0.1",
                                   handlers(sessions, authentication, profile, settings),
                                   Set.of());
        server.run();
    }

    private static DefaultServerHandler handlers(UiSessions sessions,
                                                 AccountAuthentication authentication,
                                                 ProfileHandler profile,
                                                 SettingsHandler settings) {
        return new DefaultServerHandler() {
            @Override
            public LoginService.Iface getLoginHandler() {
                return sessions;
            }

            @Override
            public AuthService.Iface getAuthHandler() {
                return authentication;
            }

            @Override
            public ProfileService.Iface getProfileHandler() {
                return profile;
            }

            @Override
            public SettingsService.Iface getSettingsHandler() {
                return settings;
            }
        };
    }

    String origin() {
        return "http://localhost:" + server.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        try (accounts; authentication) {
            server.close();
        }
    }
}
