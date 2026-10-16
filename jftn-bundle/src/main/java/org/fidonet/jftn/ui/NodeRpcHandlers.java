package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.AuthenticatedUi;
import org.fidonet.jftn.rpc.DefaultServerHandler;
import org.fidonet.jftn.rpc.LoginService;
import org.fidonet.jftn.rpc.MessageService;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.UiService;

import org.apache.thrift.TException;

/** Binds application services to Thrift independently of listener configuration. */
final class NodeRpcHandlers extends DefaultServerHandler {
    private final RpcIdentity identity;
    private final UiService.Iface ui;

    NodeRpcHandlers(RpcIdentity identity, UiService.Iface backend) {
        this.identity = identity;
        ui = AuthenticatedUi.wrap(identity.sessions(), backend);
    }

    @Override
    public LoginService.Iface getLoginHandler() {
        return identity.sessions();
    }

    @Override
    public AuthService.Iface getAuthHandler() {
        return identity.authentication();
    }

    @Override
    public ProfileService.Iface getProfileHandler() {
        return identity.profiles();
    }

    @Override
    public UiService.Iface getUiHandler() {
        return ui;
    }

    @Override
    public MessageService.Iface getMessageHandler() {
        return area -> {
            throw new TException("Use authenticated ui service");
        };
    }
}
