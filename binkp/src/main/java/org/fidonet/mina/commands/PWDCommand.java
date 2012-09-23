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
 * Time: 3:14 PM
 */
public class PWDCommand extends MessageCommand{

    public PWDCommand() {
        super(BinkpCommand.M_PWD);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_PWD);
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        if (sessionContext.getState() == SessionState.STATE_WAITPWD) {
            if (sessionContext.getLink().getPass().equals(commandArgs)) {
                OKCommand ok = new OKCommand();
                ok.send(session, sessionContext);
            } else {
                ERRCommand error = new ERRCommand();
                sessionContext.setLastErrorMessage(String.format("Bad password \"%s\"", commandArgs));
                error.send(session, sessionContext);
            }
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getPassword().getText();
    }

    @Override
    public void send(IoSession session, SessionContext sessionContext) throws Exception {
        super.send(session, sessionContext);
        sessionContext.setState(SessionState.STATE_WAITOK);
    }
}
