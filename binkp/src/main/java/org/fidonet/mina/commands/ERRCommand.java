package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class ERRCommand extends MessageCommand{

    public ERRCommand() {
        super(BinkpCommand.M_ERR);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_ERR) && args != null;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        System.out.println("ERROR: " + commandArgs);
        session.close(true);
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getLastErrorMessage();
    }
}
