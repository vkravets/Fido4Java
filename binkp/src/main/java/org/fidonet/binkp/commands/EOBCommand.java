package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class EOBCommand extends MessageCommand{

    public EOBCommand() {
        super(BinkCommand.M_EOB);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return (command.equals(BinkCommand.M_EOB));
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        sessionContext.setReceivingIsFinish(true);
        if (sessionContext.isSendingIsFinish()) {
            Command eob = new EOBCommand();
            eob.send(session, sessionContext);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return null;
    }
}
