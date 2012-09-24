package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:15 PM
 */
public class BSYCommand extends MessageCommand{
    public BSYCommand() {
        super(BinkCommand.M_BSY);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_BSY);
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        sessionContext.setState(SessionState.STATE_BSY);
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getLastErrorMessage();
    }
}
