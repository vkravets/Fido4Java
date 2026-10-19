package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.ui.NodeRpc;
import org.fidonet.jftn.web.InitialSetup;

import java.nio.file.Path;

/** Loaded only by Full; reduced distributions need neither RPC nor browser classes. */
public final class FullProfileServices implements ProfileServices {
    @Override
    public void prepare(Path configuration) throws Exception {
        InitialSetup.prepare(configuration);
    }

    @Override
    public AutoCloseable open(JFtnConfig config,
                              MailStorage storage,
                              MailRuntime mail,
                              SessionContext context,
                              PollService polls)
            throws Exception {
        return NodeRpc.open(config, (MailDatabaseFacade) storage, mail, context, polls);
    }
}
