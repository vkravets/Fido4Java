package org.fidonet.jftn.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.HttpRpcServer;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.RpcAccounts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Starts before mail/plugins, so an unconfigured node cannot accept mail or public signup. */
public final class InitialSetup {
    private InitialSetup() {
    }

    public static void prepare(Path configuration) throws Exception {
        Path authPath = configuration.toAbsolutePath().getParent().resolve("database/rpc-auth");
        if (Files.exists(configuration)) {
            JFtnConfig config = new JFtnConfig();
            config.load(configuration.toString());
            if (!Boolean.parseBoolean(config.getValue("RpcEnabled", "false"))) {
                return;
            }
            authPath = Path.of(config.getValue("RpcAuthPath",
                                               Path.of(config.getTmpDir(), "rpc-auth").toString()));
            try (RpcAccounts accounts = new RpcAccounts(authPath)) {
                if (accounts.hasUsers() || !config.getValue("RpcPassword", "").isEmpty()) {
                    return;
                }
            }
        }
        run(configuration, authPath);
    }

    private static void run(Path configuration, Path authPath) throws Exception {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        CountDownLatch completion = new CountDownLatch(1);
        try (RpcAccounts accounts = new RpcAccounts(authPath)) {
            SettingsHandler settings = new SettingsHandler(new NodeConfigurationStore(configuration),
                                                           accounts,
                                                           new UiSessions(accounts, 3600),
                                                           authPath,
                                                           token,
                                                           completion::countDown);
            DefaultServerHandler handlers = new DefaultServerHandler() {
                @Override
                public SettingsService.Iface getSettingsHandler() {
                    return settings;
                }
            };
            try (HttpRpcServer server = new HttpRpcServer(Integer.getInteger("jftn.setup.port",
                                                                             9091),
                                                          "127.0.0.1",
                                                          handlers,
                                                          Set.of())) {
                Thread shutdown = new Thread(completion::countDown, "setup-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    server.run();
                    System.out.println("Configure JFtn: http://localhost:" + server.getLocalPort()
                            + "/#setup=" + token);
                    completion.await();
                    if (settings.status().isRequired()) {
                        throw new InterruptedException("Initial setup cancelled");
                    }
                } finally {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdown);
                    } catch (IllegalStateException shuttingDown) {
                        // JVM shutdown already owns the hook.
                    }
                }
            }
        }
    }
}
