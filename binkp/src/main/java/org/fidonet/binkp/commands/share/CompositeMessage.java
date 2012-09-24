package org.fidonet.binkp.commands.share;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.MessageCommand;
import org.fidonet.binkp.io.BinkFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/20/12
 * Time: 11:06 AM
 */
public class CompositeMessage implements Command {

    private List<MessageCommand> commands;

    public CompositeMessage(List<MessageCommand> commands) {
        this.commands = commands;
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return false;
    }

    @Override
    public void send(IoSession session, SessionContext sessionContext) throws Exception {
        List<BinkFrame> frames = new ArrayList<BinkFrame>();

        for (MessageCommand command : commands) {
            frames.add(command.getData(command.getCommandArguments(sessionContext)));
        }
        for (BinkFrame frame : frames) {
            session.write(frame);
        }
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
    }

    @Override
    public BinkFrame getRawData() {
        return null;
    }

    @Override
    public boolean isCommand() {
        return false;
    }
}
