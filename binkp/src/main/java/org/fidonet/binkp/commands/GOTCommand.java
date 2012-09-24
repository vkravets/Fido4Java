package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:11 PM
 */
public class GOTCommand extends MessageCommand {

    public GOTCommand() {
        super(BinkCommand.M_GOT);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_GOT) && args != null && args.length() > 0;
    }

    private FileInfo findSentFile(SessionContext sessionContext, FileInfo info) {
        for (FileData next : sessionContext.getSendFiles()) {
            FileInfo fileInfo = next.getInfo();
            if (fileInfo.equals(info)) {
                return fileInfo;
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
        FileData fileData = sessionContext.getRecvFiles().peek();
        FileInfo fileInfo = fileData.getInfo();
        return String.format("%s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp());
    }
}
