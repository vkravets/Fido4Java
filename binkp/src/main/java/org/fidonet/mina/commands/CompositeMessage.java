package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.io.BinkFrame;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args) {
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
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public BinkFrame getRawData() throws NotImplementedException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isCommand() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
