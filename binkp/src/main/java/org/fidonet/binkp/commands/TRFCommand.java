package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:09 PM
 */
public class TRFCommand extends NULCommand {

    @Override
    public String getArguments(SessionContext sessionContext) {
        return String.format("%s %s", sessionContext.getSendMailSize(), sessionContext.getSendFilesSize());
    }

    @Override
    protected String getPrefix() {
        return "TRF";
    }

    @Override
    public void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        String[] token = commandArgs.split(" ");
        if (token.length != 2)
            throw new UnsupportedOperationException("Invalid TRF command arguments");
        sessionContext.setRecvMailSize(Long.valueOf(token[0]));
        sessionContext.setRecvFilesSize(Long.valueOf(token[1]));
    }
}
