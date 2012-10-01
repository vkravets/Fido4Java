package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;
import org.fidonet.binkp.events.ConnectedEvent;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FilesSender;

import java.io.InputStream;
import java.util.Deque;

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

        sessionContext.sendEvent(new ConnectedEvent(sessionContext));

        // Password was right init file sending
        Command traffic = new TRFCommand();
        traffic.send(session, sessionContext);

        Deque<FileData<InputStream>> files = sessionContext.getSendFiles();
        // Run thread to sending files in client mode
        FilesSender filesSender = new FilesSender(session, files, sessionContext);
        session.setAttribute(FilesSender.FILESENDER_KEY, filesSender);
        Thread sendFiles = new Thread(filesSender);
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
