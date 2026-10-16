package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountAuthentication;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import java.nio.file.Path;

/** Account persistence, sessions and passkey authentication, shared by every transport. */
final class RpcIdentity implements AutoCloseable {
    private final RpcResources resources;
    private final UiSessions sessions;
    private final AccountAuthentication authentication;

    private RpcIdentity(RpcResources resources,
                        UiSessions sessions,
                        AccountAuthentication authentication) {
        this.resources = resources;
        this.sessions = sessions;
        this.authentication = authentication;
    }

    static RpcIdentity open(JFtnConfig config) throws Exception {
        RpcResources resources = new RpcResources();
        try {
            Path path = Path.of(config.getValue("RpcAuthPath",
                                                Path.of(config.getTmpDir(), "rpc-auth")
                                                    .toString()));
            RpcAccounts accounts = resources.add(new RpcAccounts(path));
            accounts.bootstrap(config.getValue("RpcUser", ""), config.getValue("RpcPassword", ""));
            UiSessions sessions = new UiSessions(accounts,
                                                 Long.parseLong(config.getValue("RpcSessionSeconds",
                                                                                "3600")));
            resources.add((AutoCloseable) sessions::clear);
            AccountAuthentication authentication = resources.add(authentication(config,
                                                                                accounts,
                                                                                sessions));
            return new RpcIdentity(resources, sessions, authentication);
        } catch (Exception failure) {
            resources.closeAfterFailure(failure);
            throw failure;
        }
    }

    private static AccountAuthentication authentication(JFtnConfig config,
                                                        RpcAccounts accounts,
                                                        UiSessions sessions) {
        boolean passkeys = RpcConfiguration.enabled(config, "RpcPasskeysEnabled");
        String rpId = passkeys ? config.getValue("RpcWebAuthnRpId", "") : "";
        if (passkeys && rpId.isEmpty()) {
            throw new IllegalArgumentException("RpcPasskeysEnabled requires RpcWebAuthnRpId");
        }
        return new AccountAuthentication(accounts,
                                         sessions,
                                         rpId,
                                         RpcConfiguration.origins(config, "RpcWebAuthnOrigin"));
    }

    UiSessions sessions() {
        return sessions;
    }

    AccountAuthentication authentication() {
        return authentication;
    }

    @Override
    public void close() {
        resources.close();
    }
}
