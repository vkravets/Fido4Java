package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.ServerRole;
import org.fidonet.mina.SessionContext;
import org.fidonet.mina.SessionState;
import org.fidonet.mina.commands.share.Command;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:44 PM
 */
public class ADRCommand extends MessageCommand {

    public ADRCommand() {
        super(BinkpCommand.M_ADR);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_ADR) && args != null && args.length() > 0;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        if (sessionContext.getServerRole().equals(ServerRole.CLIENT)) {
            Command pwd = new PWDCommand();
            pwd.send(session, sessionContext);
        } else {
            sessionContext.setState(SessionState.STATE_WAITPWD);
        }

    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getLink().getMyaddr().toString();
    }
}
