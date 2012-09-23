package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.SessionContext;
import org.fidonet.mina.SessionState;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:15 PM
 */
public class BSYCommand extends MessageCommand{
    public BSYCommand() {
        super(BinkpCommand.M_BSY);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_BSY);
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
