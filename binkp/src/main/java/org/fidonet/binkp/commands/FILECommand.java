package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
        Deque<FileData<OutputStream>> receivedFiles = sessionContext.getRecvFiles();
        FileData curFile = receivedFiles.peek();
        if (curFile != null && !curFile.getInfo().isFinished()) {
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }
        FileInfo fileInfo = FileInfo.parseFileInfo(commandArgs);
        FileData fileData = new FileData<OutputStream>(fileInfo, new ByteArrayOutputStream());
        receivedFiles.addFirst(fileData);
        // process NR mode
        if (fileInfo.getOffset() < 0) {
            fileInfo.setOffset(0);
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        Iterator<FileData<InputStream>> iterator = sessionContext.getSendFiles().iterator();
        boolean isSent = true;
        FileInfo fileInfo = null;
        while (isSent && iterator.hasNext() ) {
            FileData fileData = iterator.next();
            fileInfo = fileData.getInfo();
            isSent = fileInfo.getCurSize() == fileInfo.getSize();
        }
        if (fileInfo != null) {
            long offset = fileInfo.getOffset();
            if (sessionContext.getStationConfig().isNRMode() && offset == 0) {
                fileInfo.setOffset(-1);
            }
            return String.format("%s %s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp(), offset);

        }
        return null;
    }
}
