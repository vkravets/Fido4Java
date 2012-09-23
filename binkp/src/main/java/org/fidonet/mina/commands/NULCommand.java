package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.SessionContext;

import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:15 PM
 */
public abstract class NULCommand extends MessageCommand{

    public NULCommand() {
        super(BinkpCommand.M_NUL);
    }

    protected abstract String getPrefix();
    protected abstract String getArguments(SessionContext sessionContext);
    protected void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {}

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return String.format("%s %s", getPrefix(), getArguments(sessionContext));
    }

    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_NUL) && args != null && args.startsWith(getPrefix());
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        String safePrefix = Pattern.quote(getPrefix());
        String args = commandArgs.replaceFirst(safePrefix, "");
        handleCommand(session, sessionContext, args.trim());
    }
}
