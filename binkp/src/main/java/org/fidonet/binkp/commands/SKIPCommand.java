package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;
import org.fidonet.binkp.io.FilesSender;

import java.io.OutputStream;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/25/12
 * Time: 2:35 PM
 */
public class SKIPCommand extends MessageCommand{

    public SKIPCommand() {
        super(BinkCommand.M_SKIP);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_SKIP) && args != null && args.length() > 0;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        FilesSender filesSender = (FilesSender) session.getAttribute(FilesSender.FILESENDER_KEY);
        FileInfo info = FileInfo.parseFileInfo(commandArgs);
        filesSender.skip(info, false);
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        FileData<OutputStream> fileData = sessionContext.getRecvFiles().peek();
        if (fileData != null) {
            FileInfo info = fileData.getInfo();
            return String.format("%s %s %s", info.getName(), info.getSize(), info.getTimestamp());
        }
        return null;
    }
}
