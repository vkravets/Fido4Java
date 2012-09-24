package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;
import org.fidonet.binkp.io.FilesSender;

import java.io.File;
import java.io.FileInputStream;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class OKCommand extends MessageCommand{

    public OKCommand() {
        super(BinkCommand.M_OK);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_OK);
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {

        if (commandArgs != null && commandArgs.length() > 0) {
            sessionContext.setSecureSession(commandArgs.startsWith("secure"));
        }

        sessionContext.setState(SessionState.STATE_IDLE);

        // Password was right init file sending
        Command traffic = new TRFCommand();
        traffic.send(session, sessionContext);

        Deque<FileInfo> files = sessionContext.getSendFiles();
        Deque<FileData> sendingFiles = new LinkedBlockingDeque<FileData>();
        Iterator<FileInfo> filesIterator = files.descendingIterator();
        while(filesIterator.hasNext()) {
            FileInfo info = filesIterator.next();
            File file = new File(sessionContext.getLink().getBoxPath() + File.separator + info.getName());
            if (file.exists()) {
                sendingFiles.addFirst(new FileData(info, new FileInputStream(file)));
            }
        }

        // Run thread to sending files
        Thread sendFiles = new Thread(new FilesSender(session, sendingFiles, sessionContext));
        sendFiles.start();
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        if (sessionContext.getPassword() != null) {
            return "secure";
        }
        return "insecure";
    }

}
