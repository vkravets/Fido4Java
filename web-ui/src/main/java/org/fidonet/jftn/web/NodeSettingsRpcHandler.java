package org.fidonet.jftn.web;

import org.fidonet.config.ParseConfigException;
import org.fidonet.jftn.rpc.DirectoryPage;
import org.fidonet.jftn.rpc.MailLinkConfiguration;
import org.fidonet.jftn.rpc.NetmailRoutingPolicy;
import org.fidonet.jftn.rpc.NodeConfiguration;
import org.fidonet.jftn.rpc.NodeLookup;
import org.fidonet.jftn.rpc.SettingsService;
import org.fidonet.jftn.rpc.SetupStatus;
import org.fidonet.jftn.rpc.TosserConfiguration;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.auth.AccountDirectory;
import org.fidonet.jftn.rpc.auth.RpcAccounts;
import org.fidonet.nodelist.DirectoryCatalog;
import org.fidonet.nodelist.DirectoryEntry;
import org.fidonet.nodelist.DirectorySource;
import org.fidonet.nodelist.HttpDirectoryDownload;
import org.fidonet.nodelist.InternetEndpoint;
import org.fidonet.types.FTNAddr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

/** Configuration authorization is independent of the chosen RPC transport. */
public final class NodeSettingsRpcHandler implements SettingsService.Iface {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeSettingsRpcHandler.class);
    private final NodeConfigurationService store;
    private final RpcAccounts accounts;
    private final UiSessions sessions;
    private final Path authPath;
    private final String setupToken;
    private final Runnable completed;
    private boolean saved;

    public NodeSettingsRpcHandler(NodeConfigurationService store,
                                  RpcAccounts accounts,
                                  UiSessions sessions,
                                  Path authPath,
                                  String setupToken,
                                  Runnable completed) {
        this.store = store;
        this.accounts = accounts;
        this.sessions = sessions;
        this.authPath = authPath;
        this.setupToken = setupToken;
        this.completed = completed;
    }

    @Override
    public DirectoryPage searchDirectory(String query, int offset, int limit, boolean includePoints)
            throws UiError {
        return call(() -> DirectoryBrowser.search(store.catalogPath(),
                                                  query,
                                                  offset,
                                                  limit,
                                                  includePoints));
    }

    @Override
    public synchronized SetupStatus status() {
        return new SetupStatus(!setupToken.isEmpty() && !saved, saved);
    }

    private void authorize(String authorization) throws UiError {
        if (setupToken.isEmpty()) {
            sessions.requireAdminUser(sessions.require(authorization));
            return;
        }
        if (saved || authorization == null
                || !MessageDigest.isEqual(setupToken.getBytes(StandardCharsets.UTF_8),
                                          authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new UiError("FORBIDDEN", "Open the setup URL printed in the server console");
        }
    }

    @Override
    public synchronized NodeConfiguration current(String authorization) throws UiError {
        authorize(authorization);
        return call(store::current);
    }

    @Override
    public synchronized NodeLookup lookup(String authorization,
                                          String address,
                                          String url,
                                          String charset)
            throws UiError {
        authorize(authorization);
        return call(() -> lookupDirectory(address, url, charset));
    }

    private NodeLookup lookupDirectory(String address, String url, String charset)
            throws Exception {
        FTNAddr target = new FTNAddr(AccountDirectory.address(address, false));
        NodeLookup result = new NodeLookup(false, false, "", "", "", "", 24554, "");
        try (DirectoryCatalog catalog = new DirectoryCatalog(store.catalogPath())) {
            if (url != null && !url.isBlank()) {
                DirectorySource source = new DirectorySource("nodes",
                                                             DirectorySource.Format.NODELIST,
                                                             store.directory()
                                                                  .resolve("database/nodelist/nodelist"),
                                                             0,
                                                             0,
                                                             Charset.forName(charset),
                                                             true);
                HttpDirectoryDownload.install(catalog,
                                              source,
                                              URI.create(url),
                                              store.receivedArchivePath());
                // Retire the earlier setup alias only after its canonical replacement imported.
                catalog.removeSource("setup-nodelist");
            }
            List<DirectoryEntry> entries = catalog.lookup(target);
            if (entries.isEmpty() && target.getPnt() != 0) {
                String boss = address.substring(0, address.indexOf('.'));
                entries = catalog.lookup(new FTNAddr(boss));
                result.setBoss(!entries.isEmpty());
            }
            if (entries.isEmpty()) {
                return result;
            }
            DirectoryEntry entry = entries.get(0);
            result.setFound(true);
            result.setSystem(entry.getSystem().replace('_', ' '));
            result.setSysop(result.isBoss() ? "" : entry.getSysop());
            result.setLocation(entry.getLocation());
            result.setSource(entry.getSource());
            List<InternetEndpoint> endpoints = entry.getBinkpEndpoints();
            if (!endpoints.isEmpty()) {
                result.setHost(endpoints.get(0).getHost());
                result.setPort(endpoints.get(0).getPort());
            }
            return result;
        }
    }

    @Override
    public synchronized boolean save(String authorization, NodeConfiguration configuration)
            throws UiError {
        authorize(authorization);
        return call(() -> {
            store.validate(configuration);
            if (!setupToken.isEmpty()) {
                bootstrap(configuration);
            }
            store.save(configuration, authPath);
            saved = true;
            completed.run();
            return true;
        });
    }

    private void authorizeMailSettings(String authorization) throws UiError {
        if (!setupToken.isEmpty()) {
            throw new UiError("FORBIDDEN", "Complete setup and sign in as administrator first");
        }
        sessions.requireAdminUser(sessions.require(authorization));
    }

    @Override
    public synchronized boolean saveNetmailRouting(String authorization,
                                                   NetmailRoutingPolicy policy)
            throws UiError {
        authorizeMailSettings(authorization);
        return call(() -> {
            NetmailRoutingSettings.save(store, policy);
            saved = true;
            return true;
        });
    }

    @Override
    public synchronized List<MailLinkConfiguration> links(String authorization) throws UiError {
        authorizeMailSettings(authorization);
        return call(() -> new MailConfigurationStore(store).links());
    }

    @Override
    public synchronized boolean saveLinks(String authorization, List<MailLinkConfiguration> links)
            throws UiError {
        authorizeMailSettings(authorization);
        return call(() -> {
            new MailConfigurationStore(store).saveLinks(links);
            saved = true;
            return true;
        });
    }

    @Override
    public synchronized boolean reload(String authorization) throws UiError {
        authorizeMailSettings(authorization);
        return call(() -> {
            store.reload();
            return true;
        });
    }

    @Override
    public synchronized TosserConfiguration tosser(String authorization) throws UiError {
        authorizeMailSettings(authorization);
        return call(() -> new MailConfigurationStore(store).tosser());
    }

    @Override
    public synchronized boolean saveTosser(String authorization, TosserConfiguration configuration)
            throws UiError {
        authorizeMailSettings(authorization);
        return call(() -> {
            new MailConfigurationStore(store).saveTosser(configuration);
            saved = true;
            return true;
        });
    }

    private void bootstrap(NodeConfiguration configuration) throws Exception {
        if (configuration.getPassword() == null || configuration.getPassword().length() < 12) {
            throw new IllegalArgumentException("Administrator password needs at least 12 characters");
        }
        // If publication was interrupted after account creation, only that account can resume.
        if (accounts.hasUsers()) {
            if (!accounts.verifyPassword(configuration.getLogin(), configuration.getPassword())
                    || !new AccountDirectory(accounts).profile(configuration.getLogin()).role.equals("ADMIN")) {
                throw new IllegalArgumentException("Existing administrator credentials are required");
            }
            return;
        }
        accounts.createUser(configuration.getLogin(), configuration.getPassword());
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run() throws Exception;
    }

    private static <T> T call(Operation<T> operation) throws UiError {
        try {
            return operation.run();
        } catch (ParseConfigException failure) {
            LOGGER.warn("Configuration candidate was rejected", failure);
            throw new UiError("INVALID_CONFIGURATION",
                              "Invalid configuration: " + failure.getMessage());
        } catch (IllegalArgumentException failure) {
            throw new UiError("INVALID_ARGUMENT", failure.getMessage());
        } catch (Exception failure) {
            LOGGER.error("Node configuration operation failed", failure);
            throw new UiError("SETTINGS_FAILED",
                              "Configuration operation failed; check paths, source and server log");
        }
    }
}
