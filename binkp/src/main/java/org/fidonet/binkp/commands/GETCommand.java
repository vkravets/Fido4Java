package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:19 PM
 */
public class GETCommand extends MessageCommand {

    public GETCommand() {
        super(BinkCommand.M_GET);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_GET) && args != null && args.length() > 0;
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
