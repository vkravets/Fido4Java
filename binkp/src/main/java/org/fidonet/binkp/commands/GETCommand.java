package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.io.FileInfo;

import java.util.Deque;

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
        // TODO handle resend
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        Deque<FileInfo> receivedFiles = sessionContext.getRecvFiles();
        FileInfo curFile = receivedFiles.peek();
        return String.format("%s %s %s %s", curFile.getName(), curFile.getSize(), curFile.getTimestamp(), curFile.getCurSize());
    }
}
