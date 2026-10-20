package org.fidonet.jftn.ui;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.jftn.LiveConfiguration;
import org.fidonet.jftn.MailRuntime;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.ui.transport.RpcTransports;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Owns the optional node RPC runtime. Close before the mail service and database. */
public final class NodeRpc implements AutoCloseable {
    private final RpcResources resources;
    private final RpcTransports transports;

    private NodeRpc(RpcResources resources, RpcTransports transports) {
        this.resources = resources;
        this.transports = transports;
    }

    public static NodeRpc open(JFtnConfig config,
                               MailDatabaseFacade base,
                               MailRuntime mail,
                               SessionContext context)
            throws Exception {
        return open(config, base, mail, context, null);
    }

    public static NodeRpc open(JFtnConfig config,
                               MailDatabaseFacade base,
                               MailRuntime mail,
                               SessionContext context,
                               PollService sharedPolls)
            throws Exception {
        if (!RpcConfiguration.enabled(config, "RpcEnabled")) {
            return null;
        }
        RpcResources resources = new RpcResources();
        try {
            RpcIdentity identity = resources.add(RpcIdentity.open(config));
            PollService polls = sharedPolls == null
                    ? resources.add(new PollService(config, context))
                    : sharedPolls;
            identity.reloadWith(new LiveConfiguration(config,
                                                      base,
                                                      mail,
                                                      polls,
                                                      context,
                                                      identity.profiles()));
            NodeUiServiceHandler backend = new NodeUiServiceHandler(config,
                                                                    base,
                                                                    mail,
                                                                    polls,
                                                                    identity.sessions());
            mail.operate(tosser -> {
                tosser.hostedMailboxes(() -> hostedAddresses(identity));
                return null;
            });
            resources.add((AutoCloseable) () -> mail.operate(tosser -> {
                tosser.hostedMailboxes(Set::of);
                return null;
            }));
            NodeRpcHandlers handlers = new NodeRpcHandlers(identity, backend);
            RpcTransports transports = resources.add(RpcTransports.open(config, handlers));
            return new NodeRpc(resources, transports);
        } catch (Exception failure) {
            resources.closeAfterFailure(failure);
            throw failure;
        }
    }

    private static Set<FTNAddr> hostedAddresses(RpcIdentity identity) throws IOException {
        Set<FTNAddr> addresses = new LinkedHashSet<>();
        for (String address : identity.sessions().hostedMailAddresses()) {
            addresses.add(new FTNAddr(address));
        }
        return addresses;
    }

    public int getPort(String transport) {
        return transports.getPort(transport);
    }

    public int getPort() {
        return getPort("tcp");
    }

    public int getHttpPort() {
        return getPort("http");
    }

    @Override
    public void close() {
        resources.close();
    }
}
