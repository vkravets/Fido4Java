package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class EOBCommand extends MessageCommand{

    public EOBCommand() {
        super(BinkpCommand.M_EOB);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return (command.equals(BinkpCommand.M_EOB));
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {

    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return null;
    }
}
