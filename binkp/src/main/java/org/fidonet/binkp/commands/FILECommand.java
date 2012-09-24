package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.io.FileInfo;

import java.util.Deque;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:48 PM
 */
public class FILECommand extends MessageCommand {

    public FILECommand() {
        super(BinkCommand.M_FILE);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_FILE) && args != null && args.length() > 0;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        sessionContext.setState(SessionState.STATE_TRANSFER);
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

        Iterator<FileInfo> iterator = sessionContext.getSendFiles().iterator();
        boolean isSent = true;
        FileInfo fileInfo = null;
        while (isSent && iterator.hasNext() ) {
            fileInfo = iterator.next();
            isSent = fileInfo.getCurSize() == fileInfo.getSize();
        }
        return String.format("%s %s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp(), fileInfo.getOffset());
    }
}
