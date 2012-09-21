package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.io.FileInfo;

import java.util.Deque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:48 PM
 */
public class FILECommand extends MessageCommand {

    public FILECommand() {
        super(BinkpCommand.M_FILE);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_FILE) && args != null && args.length() > 0;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        Deque<FileInfo> receivedFiles = sessionContext.getRecvFiles();
        FileInfo curFile = receivedFiles.peek();
        if (curFile != null && !curFile.isFinished()) {
            // TODO send resend(M_GET) since old file is not recieved fully
        }
        FileInfo fileInfo = FileInfo.parseFileInfo(commandArgs);
        receivedFiles.addFirst(fileInfo);
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        // todo pop from files queue in session context first item
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
