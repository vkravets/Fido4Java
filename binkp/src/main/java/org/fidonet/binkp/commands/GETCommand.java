package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;
import org.fidonet.binkp.io.FilesSender;

import java.io.OutputStream;
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
        final FilesSender filesSender = (FilesSender) session.setAttribute(FilesSender.FILESENDER_KEY);
        if (filesSender != null) {
            final FileInfo fileInfo = FileInfo.parseFileInfo(commandArgs);
            if (sessionContext.getState().equals(SessionState.STATE_WAITGET)) {
                Thread sendFileBegin = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            filesSender.getExchanger().exchange(fileInfo);
                        } catch (InterruptedException ignored) {}
                    }
                });
                sendFileBegin.join();
            } else {
                filesSender.skip(fileInfo, true);
            }
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        Deque<FileData<OutputStream>> receivedFiles = sessionContext.getRecvFiles();
        FileData curFile = receivedFiles.peek();
        if (curFile != null) {
            FileInfo info = curFile.getInfo();
            return String.format("%s %s %s %s", info.getName(), info.getSize(), info.getTimestamp(), info.getCurSize());
        }
        return null;
    }
}
