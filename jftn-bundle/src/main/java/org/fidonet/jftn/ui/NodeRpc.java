package org.fidonet.jftn.ui;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.jftn.MailService;
import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.AuthenticatedUi;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.MessageService;
import org.fidonet.jftn.rpc.Server;
import org.fidonet.jftn.rpc.UiService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountAuthentication;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import org.apache.thrift.TException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns optional RPC sockets, sessions and operator polls. Close before the mail database. */
public final class NodeRpc implements AutoCloseable {
    private final Server server;
    private final NodeUi backend;
    private final UiSessions sessions;
    private final RpcAccounts accounts;
    private final AccountAuthentication authentication;

    private NodeRpc(Server server,
                    NodeUi backend,
                    UiSessions sessions,
                    RpcAccounts accounts,
                    AccountAuthentication authentication) {
        this.server = server;
        this.backend = backend;
        this.sessions = sessions;
        this.accounts = accounts;
        this.authentication = authentication;
    }

    public static NodeRpc open(JFtnConfig config,
                               DatabaseManager base,
                               MailService mail,
                               SessionContext context)
            throws Exception {
        String enabled = config.getValue("RpcEnabled", "false").trim();
        if (enabled.equalsIgnoreCase("false")) {
            return null;
        }
        if (!enabled.equalsIgnoreCase("true")) {
            throw new IllegalArgumentException("RpcEnabled must be true or false");
        }
        RpcAccounts accounts = new RpcAccounts(Path.of(config.getValue("RpcAuthPath",
                                                                       Path.of(config.getTmpDir(),
                                                                               "rpc-auth")
                                                                           .toString())));
        try {
            return start(config, base, mail, context, accounts);
        } catch (Exception failure) {
            try {
                accounts.close();
            } catch (IOException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    private static NodeRpc start(JFtnConfig config,
                                 DatabaseManager base,
                                 MailService mail,
                                 SessionContext context,
                                 RpcAccounts accounts)
            throws Exception {
        accounts.bootstrap(config.getValue("RpcUser", ""), config.getValue("RpcPassword", ""));
        UiSessions sessions = new UiSessions(accounts,
                                             Long.parseLong(config.getValue("RpcSessionSeconds",
                                                                            "3600")));
        String enabled = config.getValue("RpcPasskeysEnabled", "false").trim();
        if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("RpcPasskeysEnabled must be true or false");
        }
        String rpId = Boolean.parseBoolean(enabled) ? config.getValue("RpcWebAuthnRpId", "") : "";
        if (Boolean.parseBoolean(enabled) && rpId.isEmpty()) {
            throw new IllegalArgumentException("RpcPasskeysEnabled requires RpcWebAuthnRpId");
        }
        List<String> configuredOrigins = config.getValuesAsList("RpcWebAuthnOrigin");
        Set<String> origins = configuredOrigins == null
                ? Set.of()
                : new HashSet<>(configuredOrigins);
        AccountAuthentication authentication = new AccountAuthentication(accounts,
                                                                         sessions,
                                                                         rpId,
                                                                         origins);
        NodeUi backend = new NodeUi(config, base, mail, context);
        try {
            UiService.Iface api = AuthenticatedUi.wrap(sessions, backend);
            Server server = new Server(Integer.parseInt(config.getValue("RpcPort", "9090")),
                                       config.getValue("RpcBind", "127.0.0.1"),
                                       new DefaultServerHandler() {
                                           @Override
                                           public LoginService.Iface getLoginHandler() {
                                               return sessions;
                                           }

                                           @Override
                                           public AuthService.Iface getAuthHandler() {
                                               return authentication;
                                           }

                                           @Override
                                           public UiService.Iface getUiHandler() {
                                               return api;
                                           }

                                           @Override
                                           public MessageService.Iface getMessageHandler() {
                                               return area -> {
                                                   throw new TException("Use authenticated ui service");
                                               };
                                           }
                                       });
            NodeRpc rpc = new NodeRpc(server, backend, sessions, accounts, authentication);
            try {
                server.run();
                return rpc;
            } catch (RuntimeException failure) {
                server.close();
                throw failure;
            }
        } catch (Exception failure) {
            authentication.close();
            backend.close();
            throw failure;
        }
    }

    public int getPort() {
        return server.getLocalPort();
    }

    @Override
    public void close() {
        try {
            server.close();
        } finally {
            sessions.clear();
            authentication.close();
            try {
                backend.close();
            } finally {
                try {
                    accounts.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
