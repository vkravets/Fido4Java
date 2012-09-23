package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.SessionContext;
import org.fidonet.mina.io.FileInfo;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:11 PM
 */
public class GOTCommand extends MessageCommand {

    public GOTCommand() {
        super(BinkpCommand.M_GOT);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
        return command.equals(BinkpCommand.M_GOT) && args != null && args.length() > 0;
    }

    private FileInfo findSentFile(SessionContext sessionContext, FileInfo info) {
        for (FileInfo next : sessionContext.getSendFiles()) {
            if (next.equals(info)) {
                return next;
            }
        }
        return null;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        FileInfo info = FileInfo.parseFileInfo(commandArgs);
        FileInfo sentFile = findSentFile(sessionContext, info);
        if (sentFile != null) {
            sentFile.setFinished(true);
            long totalSize = sessionContext.getRecvFilesSize() + sentFile.getSize();
            sessionContext.setRecvFilesSize(totalSize);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        FileInfo fileInfo = sessionContext.getRecvFiles().peek();
        return String.format("%s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp());
    }
}
