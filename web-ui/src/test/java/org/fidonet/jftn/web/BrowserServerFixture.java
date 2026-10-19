package org.fidonet.jftn.web;

import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.AuthenticatedUi;
import org.fidonet.jftn.rpc.DefaultRpcServiceProvider;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountAuthentication;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.ProfileHandler;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import java.io.IOException;
import java.nio.file.Files;
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
        Path directoryPath = BrowserDirectoryFixture.create(directory);
        Path authPath = directory.resolve("accounts");
        accounts = new RpcAccounts(authPath);
        if (setupToken.isEmpty()) {
            accounts.createUser("admin", "preview-admin-password");
            Files.writeString(directory.resolve("jftn.conf"),
                              "System=Browser test\nSysOp=Test\nLink=1:500/101,1:500/100,preview,example.org:24554\n"
                                      + "Inbound=" + directory.resolve("inbound") + "\n"
                                      + "Outbound=" + directory.resolve("outbound") + "\n"
                                      + "EchoPath=" + directory.resolve("echo") + "\n" + "Tmp="
                                      + directory.resolve("tmp")
                                      + "\nEchoSubscriptions=database\nNodeDirectoryPath="
                                      + directoryPath + "\n");
        }
        UiSessions sessions = new UiSessions(accounts, 3600);
        authentication = new AccountAuthentication(accounts, sessions, "", Set.of());
        ProfileHandler profile = new ProfileHandler(new AccountDirectory(accounts),
                                                    sessions,
                                                    List.of("1:500/100"),
                                                    true);
        NodeSettingsRpcHandler settings = new NodeSettingsRpcHandler(new NodeConfigurationService(directory.resolve("jftn.conf")),
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

    private static DefaultRpcServiceProvider handlers(UiSessions sessions,
                                                      AccountAuthentication authentication,
                                                      ProfileHandler profile,
                                                      NodeSettingsRpcHandler settings) {
        UiService.Iface echoAccess = AuthenticatedUi.wrap(sessions,
                                                          new BrowserEchoAccess().handler(),
                                                          true);
        return new DefaultRpcServiceProvider() {
            @Override
            public UiService.Iface getUiHandler() {
                return echoAccess;
            }

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
