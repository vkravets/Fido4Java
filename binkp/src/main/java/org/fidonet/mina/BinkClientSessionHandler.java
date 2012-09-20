package org.fidonet.mina;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.codec.FileData;
import org.fidonet.mina.commands.*;
import org.fidonet.mina.io.BinkData;
import org.fidonet.mina.io.BinkFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:18 PM
 */
public class BinkClientSessionHandler extends IoHandlerAdapter{

    private SessionContext sessionContext;

    public BinkClientSessionHandler(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        super.sessionOpened(session);    //To change body of overridden methods use File | Settings | File Templates.

        List<MessageCommand> commands = new ArrayList<MessageCommand>();
        commands.add(new SYSCommand());
        commands.add(new ZYZCommand());
        commands.add(new LOCCommand());
        commands.add(new NDLCommand());
        commands.add(new VERCommand());
        commands.add(new TIMECommand());
        commands.add(new ADRCommand());
        commands.add(new PWDCommand());

        CompositeMessage greeting = new CompositeMessage(commands);
        greeting.send(session, sessionContext);

    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        cause.printStackTrace();
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        BinkFrame data = (BinkFrame) message;
        BinkData binkData = BinkFrame.toBinkData(data);
        Command command = CommandFactory.createCommand(sessionContext, binkData);
        if (command != null) {
            System.out.println(BinkpCommand.findCommand(binkData.getCommand()));
            System.out.println(new String(binkData.getData()));
            command.handle(session, sessionContext, new String(binkData.getData()));
        } else {
            // try to get data bulk
            FileData dataFile = new FileData(binkData.getData());
            // process data bulk
            System.out.println("Received data block with size " +dataFile.getRawData().getData().length  +" bytes");
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        super.sessionIdle(session, status);  //To change body of overridden methods use File | Settings | File Templates.
    }
}
