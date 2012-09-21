package org.fidonet.mina.commands;

import com.sun.istack.internal.NotNull;
import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.io.FileInfo;

import java.util.Iterator;

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
        Iterator<FileInfo> iterator = sessionContext.getSendFiles().iterator();
        boolean found = false;
        FileInfo result = null;
        while(found || iterator.hasNext()) {
            FileInfo next = iterator.next();
            found = next.equals(info);
            if (found) {
                result = next;
            }
        }
        return result;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        FileInfo info = FileInfo.parseFileInfo(commandArgs);
        FileInfo sentFile = findSentFile(sessionContext, info);
        if (sentFile != null) {
            sentFile.setFinished(true);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        @NotNull FileInfo fileInfo = sessionContext.getRecvFiles().peek();
        return String.format("%s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp());
    }
}
