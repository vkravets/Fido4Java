package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.config.Password;
import org.fidonet.binkp.events.ConnectedEvent;
import org.fidonet.binkp.events.DisconnectedEvent;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class PWDCommand extends MessageCommand{

    public PWDCommand() {
        super(BinkCommand.M_PWD);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_PWD);
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        if (sessionContext.getState() == SessionState.STATE_WAITPWD) {
            Password password = sessionContext.getPassword();
            Password remotePassword = new Password(commandArgs.trim(), password.isCrypt(), password.getMessageDigest(), password.getKey());
            if (password.getText() == remotePassword.getText()) {
                OKCommand ok = new OKCommand();
                ok.send(session, sessionContext);
                sessionContext.sendEvent(new ConnectedEvent(sessionContext));
            } else {
                ERRCommand error = new ERRCommand();
                sessionContext.setState(SessionState.STATE_ERR);
                sessionContext.setLastErrorMessage(String.format("Bad password \"%s\"", commandArgs));
                error.send(session, sessionContext);
                sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
                session.close(false);
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
