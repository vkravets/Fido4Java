package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:19 PM
 */
public class GETCommand extends MessageCommand {

    public GETCommand() {
        super(BinkpCommand.M_GET);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_GET) && args != null && args.length() > 0;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        // pop from queue of recieved file in session context
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
