package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.config.ServerRole;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class ERRCommand extends MessageCommand{

    public ERRCommand() {
        super(BinkCommand.M_ERR);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_ERR) && args != null;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        System.out.println("ERROR: " + commandArgs);
        sessionContext.setLastErrorMessage(commandArgs);
        if (sessionContext.getServerRole().equals(ServerRole.CLIENT)) {
            sessionContext.setState(SessionState.STATE_ERR);
        } else {
            session.close(false);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getLastErrorMessage();
    }
}
