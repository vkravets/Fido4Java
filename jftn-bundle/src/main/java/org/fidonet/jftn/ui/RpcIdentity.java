package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountAuthentication;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.ProfileHandler;
import org.fidonet.jftn.rpc.auth.RpcAccounts;
import org.fidonet.jftn.web.NodeConfigurationService;
import org.fidonet.jftn.web.NodeSettingsRpcHandler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Account persistence, sessions and passkey authentication, shared by every transport. */
final class RpcIdentity implements AutoCloseable {
    private final RpcResources resources;
    private final UiSessions sessions;
    private final AccountAuthentication authentication;
    private final ProfileHandler profiles;
    private SettingsService.Iface settings;
    private NodeConfigurationService.Application reload = (candidate, publication) -> {
        throw new IllegalArgumentException("Live configuration is not available");
    };

    void reloadWith(NodeConfigurationService.Application application) {
        reload = application;
    }

    private RpcIdentity(RpcResources resources,
                        UiSessions sessions,
                        AccountAuthentication authentication,
                        ProfileHandler profiles) {
        this.resources = resources;
        this.sessions = sessions;
        this.authentication = authentication;
        this.profiles = profiles;
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
            List<String> addresses = new ArrayList<>();
            config.getLocalAddresses().forEach(address -> addresses.add(address.toString()));
            String registration = config.getValue("RpcRegistrationEnabled", "true");
            if (!registration.equalsIgnoreCase("true") && !registration.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("RpcRegistrationEnabled must be true or false");
            }
            ProfileHandler profiles = new ProfileHandler(new AccountDirectory(accounts),
                                                         sessions,
                                                         addresses,
                                                         Boolean.parseBoolean(registration));
            RpcIdentity identity = new RpcIdentity(resources, sessions, authentication, profiles);
            if (config.getSourcePath() != null) {
                identity.settings = new NodeSettingsRpcHandler(new NodeConfigurationService(config.getSourcePath(),
                                                                                            (candidate,
                                                                                             publication) -> identity.reload.apply(candidate,
                                                                                                                                   publication)),
                                                               accounts,
                                                               sessions,
                                                               path,
                                                               "",
                                                               () -> {
                                                               });
            }
            return identity;
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

    ProfileHandler profiles() {
        return profiles;
    }

    SettingsService.Iface settings() {
        return settings;
    }

    AccountAuthentication authentication() {
        return authentication;
    }

    @Override
    public void close() {
        resources.close();
    }
}
