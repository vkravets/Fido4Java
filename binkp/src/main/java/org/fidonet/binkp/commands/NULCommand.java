package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;

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
        super(BinkCommand.M_NUL);
    }

    protected abstract String getPrefix();
    protected abstract String getArguments(SessionContext sessionContext);
    protected void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {}

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        String args = getArguments(sessionContext);
        args = args == null ? "":args;
        if (getPrefix() != null) {
            return String.format("%s %s", getPrefix(), args);
        }
        return args;
    }

    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_NUL) && args != null && args.startsWith(getPrefix());
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        String args = "";
        if (getPrefix() != null) {
            String safePrefix = Pattern.quote(getPrefix());
            args = commandArgs.replaceFirst(safePrefix, "").trim();
        }
        handleCommand(session, sessionContext, args);
    }
}
